/*
 * CVReturn Bukkit plugin for Minecraft
 * Copyright (C) 2020-2022 Matt Ciolkosz (https://github.com/mciolkosz)
 * Copyright (C) 2020-2022 Cubeville (https://www.cubeville.org/)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cubeville.cvreturn.bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CVReturn extends JavaPlugin implements Listener {
	
	private static final int RETURN_NORMAL = 10;
	private static final int RETURN_EXTENDED = 15;
	private static final int RETURN_ULTIMATE = 30;
	
	private ConcurrentHashMap<UUID, List<Location>> allLocations;
	private ConcurrentHashMap<UUID, Location> ignoredLocations;
	
	@Override
	public void onEnable() {
		
		this.allLocations = new ConcurrentHashMap<UUID, List<Location>>();
		this.ignoredLocations = new ConcurrentHashMap<UUID, Location>();
		this.getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		
		if (!command.getName().equals("return")) { return false; }
		
		final String extendedUsage = command.getUsage() + " [player]";
		String playerName = sender.getName();
		int count = 1;
		
		// FIRST SECTION - DETERMINE WHO IS MOVING BACKWARDS, AND HOW MANY STEPS
		// BACKWARDS THEY ARE MOVING.
		
		final boolean isPlayer = sender instanceof Player;
		
		// No args, move sender back 1 location (unless is console).
		if (args.length == 0) {
			if (!isPlayer) {
				sender.sendMessage(extendedUsage);
				return true;
			}
			
			playerName = sender.getName();
			count = 1;
		}
		
		// 1 arg, determine if it is the count or a player.
		// try {} assumes arg is the count.
		else if (args.length == 1) {
			boolean isCount = true;
			try {
				count = Integer.parseInt(args[0]);
				playerName = sender.getName();
				
				// console must specify a player
				if (!isPlayer) {
					sender.sendMessage(extendedUsage);
					return true;
				}
				
				// Check count to be outside of bounds.
				if (count <= 0 || count > 30) {
					isCount = false;
				}
			} catch (NumberFormatException e) {
				isCount = false;
			}
			
			// was a player name, not a count.
			// verify sender is a player and has the appropriate permission.
			if (!isCount) {
				if (isPlayer && !sender.hasPermission("cvreturn.return.other")) {
					sender.sendMessage(command.getPermissionMessage());
					return true;
				}
				count = 1;
				playerName = args[0];
			}
		}
		
		// 2 args, determine which is the count.
		else if (args.length == 2) {
			
			// verify sender has permission if they are a player to move
			// others backwards.
			if (isPlayer && !sender.hasPermission("cvreturn.return.other")) {
				sender.sendMessage(command.getPermissionMessage());
				return true;
			}
			
			boolean foundCount = false;
			try {
				count = Integer.parseInt(args[0]);
				playerName = args[1];
				foundCount = count > 0 && count <= 30;
			}
			catch (NumberFormatException e) {
				try {
					count = Integer.parseInt(args[1]);
					playerName = args[0];
					foundCount = count > 0 && count <= 30;
				}
				catch (NumberFormatException e1) {
					sender.sendMessage("§cThe amount specified is player-permission-dependent, but it will always be at most 30.");
					return true;
				}
			}
			
			if (!foundCount) {
				sender.sendMessage("§cThe amount specified is player-permission-dependent, but it will always be at most 30.");
				return true;
			}
		}
		
		// 3+ args, too many.
		else {
			if (!isPlayer || sender.hasPermission("cvreturn.return.other")) {
				sender.sendMessage(extendedUsage);
				return true;
			}
			else {
				return false;
			}
		}
		
		// END FIRST SECTION
		// START SECOND SECTION - ERROR CHECKING AND PLAYER TELEPORTING.
		
		// No moving the console, I don't care if you want to.
		if (playerName.equalsIgnoreCase("console")) {
			sender.sendMessage("§cYou may not return the console to its previous position. That breaks things.");
			return true;
		}
		
		// Check if the player exists and/or is online.
		final boolean samePlayer = sender.getName().equalsIgnoreCase(playerName);
		final Player player = samePlayer ? (Player) sender : this.getServer().getPlayer(playerName);
		if (player == null) {
			sender.sendMessage("§cPlayer §6" + playerName + " §cnot found.");
			return true;
		}
		playerName = player.getName();
		if (!samePlayer && !player.isOnline()) {
			sender.sendMessage("§6" + playerName + " §cis not online.");
			return true;
		}
		
		// Check for teleport history.
		final UUID uniqueId = player.getUniqueId();
		final List<Location> locations = this.allLocations.get(uniqueId);
		if (locations == null || locations.isEmpty()) {
			sender.sendMessage((samePlayer ? "§cYou have " : "§6" + playerName + " §chas ") + "no return history.");
			return true;
		}
		
		// Trying to go back too far.
		if (count > this.getAllowedAmount(player) || count > locations.size()) {
			sender.sendMessage((samePlayer ? "§cYou " : "§6" + playerName + " §c") + "cannot go back that far.");
			sender.sendMessage((samePlayer ? "§cYou " : "§6" + playerName + " §c") + "can return §b" + locations.size() + " §ctimes.");
			return true;
		}
		
		// Move backwards.
		for (int i = 0; i < count; i++) {
			final Location location = locations.remove(0);
			if (i != count - 1) { continue; }
			
			// Somehow ran into an issue.
			if (location == null) {
				sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
				this.getLogger().log(Level.WARNING, "Location number " + i + " was null, no good way to track down the reason why.");
				return true;
			}
			
			// Ignore the current location, otherwise there will be infinite loops.
			// Send the player back, but up .25 of a block to prevent falling thru the floor.
			this.ignoredLocations.put(uniqueId, player.getLocation());
			player.teleport(location.clone().add(0.0D, 0.25D, 0.0D));
		}
		
		// WOO HOO
		player.sendMessage("§a§oZoom!");
		final boolean isSenderInvisible = !(sender instanceof Player) || !player.canSee((Player) sender);
		if (!samePlayer) { player.sendMessage("§aYou have been teleported back §6" + count + " §alocation" + (count == 1 ? "" : "s") + " by " + (isSenderInvisible ? " a magic force." : "§6" + sender.getName() + "§a.")); }
		sender.sendMessage((samePlayer ? "§aYou have " : "§6" + playerName + " §ahas ") + "been teleported back §6" + count + " §a" + (count == 1 ? "location" : "locations") + ".");
		return true;
		
		// END SECOND SECTION
	}
	
	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String label, final String[] args) {
		
		if (!command.getName().equals("return")) { return null; }
		
		final boolean isPlayer = sender instanceof Player;
		final ArrayList<String> completions = new ArrayList<String>();
		final ArrayList<String> argsList = new ArrayList<String>(Arrays.asList(args));
		
		// No args specified yet.
		if (argsList.isEmpty()) {
			
			// If player, default to suggesting the amount backwards to move.
			if (isPlayer) {
				final UUID playerId = ((Player) sender).getUniqueId();
				if (!this.allLocations.containsKey(playerId)) { return Collections.emptyList(); }
				if (this.allLocations.get(playerId) == null) { return Collections.emptyList(); }
				if (this.allLocations.get(playerId).isEmpty()) { return Collections.emptyList(); }
				
				final int allowedAmount = Math.min(this.allLocations.get(playerId).size(), this.getAllowedAmount((Player) sender));
				for (int count = 1; count <= allowedAmount; count++) { completions.add(String.valueOf(count)); }
			}
			
			// If console, default to suggesting a player.
			else {
				for (final Player player : this.getServer().getOnlinePlayers()) {
					final UUID playerId = player.getUniqueId();
					if (!this.allLocations.containsKey(playerId)) { continue; }
					if (this.allLocations.get(playerId) == null) { continue; }
					if (this.allLocations.get(playerId).isEmpty()) { continue; }
					completions.add(player.getName());
				}
			}
			return completions;
		}
		
		// Only 1 (possibly partial) arg specified.
		final String arg1 = argsList.remove(0);
		if (argsList.isEmpty()) {
			
			// If console or player has permission to return other players,
			// default to suggesting players.
			if (!isPlayer || sender.hasPermission("cvreturn.return.other")) {
				for (final Player player : this.getServer().getOnlinePlayers()) {
					if (isPlayer && !((Player) sender).canSee(player)) { continue; }
					completions.add(player.getName());
				}
				completions.removeIf(completion -> !completion.toLowerCase().startsWith(arg1.toLowerCase()));
			}
			
			// Otherwise, default to suggesting the amount to move backwards.
			else {
				final UUID playerId = ((Player) sender).getUniqueId();
				if (!this.allLocations.containsKey(playerId)) { return Collections.emptyList(); }
				if (this.allLocations.get(playerId) == null) { return Collections.emptyList(); }
				if (this.allLocations.get(playerId).isEmpty()) { return Collections.emptyList(); }
				
				final int allowedAmount = Math.min(this.allLocations.get(playerId).size(), this.getAllowedAmount((Player) sender));
				for (int count = 1; count <= allowedAmount; count++) { completions.add(String.valueOf(count)); }
			}
			return completions;
		}
		
		// More than 1 arg specified (2+ or 1 full + 1 partial).
		// Determine if the 1st argument is the amount.
		boolean isCount;
		try {
			final int count = Integer.parseInt(arg1);
			isCount = count > 0 && count <= RETURN_ULTIMATE;
		} catch (NumberFormatException e) {
			isCount = false;
		}
		
		// 1st arg was not a valid count, assume player name.
		// Completions will be the count for the player.
		if (!isCount) {
			
			final Player player = this.getServer().getPlayer(arg1);
			if (player == null) { return Collections.emptyList(); }
			if (!player.isOnline()) { return Collections.emptyList(); }
			if (isPlayer && !((Player) sender).canSee(player)) { return Collections.emptyList(); }
			
			final UUID playerId = player.getUniqueId();
			if (!this.allLocations.containsKey(playerId)) { return Collections.emptyList(); }
			if (this.allLocations.get(playerId) == null) { return Collections.emptyList(); }
			if (this.allLocations.get(playerId).isEmpty()) { return Collections.emptyList(); }
			
			final int allowedAmount = Math.min(this.allLocations.get(playerId).size(), this.getAllowedAmount(player));
			for (int count = 1; count <= allowedAmount; count++) { completions.add(String.valueOf(count)); }
		}
		
		// 1st arg is a valid count. Completions will be player names.
		else {
			for (final Player player : this.getServer().getOnlinePlayers()) {
				if (isPlayer && !((Player) sender).canSee(player)) { continue; }
				final UUID playerId = player.getUniqueId();
				if (!this.allLocations.containsKey(playerId)) { continue; }
				if (this.allLocations.get(playerId) == null) { continue; }
				if (this.allLocations.get(playerId).isEmpty()) { continue; }
				completions.add(player.getName());
			}
		}
		
		
		// Only 2 args (or 1 full + 1 partial) were specified.
		// Remove non-matching completions.
		final String arg2 = argsList.remove(0);
		if (argsList.isEmpty()) {
			completions.removeIf(completion -> !completion.toLowerCase().startsWith(arg2.toLowerCase()));
			return completions;
		}
		
		// 3+ args specified, no additional suggestions.
		return Collections.emptyList();
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerTeleport(final PlayerTeleportEvent event) {
		
		// Try to cut down on automatic lag-backs.
		final Location from = event.getFrom();
		if (from.equals(event.getTo())) { return; }
		
		final Player player = event.getPlayer();
		final UUID uniqueId = player.getUniqueId();
		List<Location> locations = this.allLocations.get(uniqueId);
		if (locations == null) { locations = new ArrayList<Location>(); }
		
		// If the from location is ignored (as probably just set in the code
		// in the method above), remove it, and do not track it as a
		// "previous location".
		final Location ignored = this.ignoredLocations.get(uniqueId);
		if (from.equals(ignored)) {
			this.ignoredLocations.remove(uniqueId);
			return;
		}
		
		// Check if the player is being teleported to the same spot repeatedly.
		// Ignore these as they may be caused by several things, including:
		// - Player teleporting to same static spot because <reasons>.
		// - 1.13+ servers laggy chunk loading, causing the server to TP the
		//   player to prevent them from falling forever.
		if (!locations.isEmpty() && locations.get(0).equals(from)) {
			return;
		}
		
		// Add the new location. Find the maximum amount the player is allowed
		// to go back, and remove the oldest ones.
		locations.add(0, from);
		int max = this.getAllowedAmount(player);
		int count = 0;
		final Iterator<Location> iter = locations.iterator();
		while (iter.hasNext()) {
			iter.next();
			count++;
			if (count <= max) { continue; }
			iter.remove();
		}
		
		// Update the latest locations map.
		this.allLocations.put(uniqueId, locations);
	}
	
	@EventHandler
	public void onPlayerCommandSend(final PlayerCommandSendEvent event) {
		
		final Player player = event.getPlayer();
		final HashSet<String> removals = new HashSet<String>();
		for (final String commandName : this.getDescription().getCommands().keySet()) {
			removals.add("cvreturn:" + commandName);
			if (!player.hasPermission(this.getServer().getPluginCommand(commandName).getPermission())) { removals.add(commandName); }
		}
		
		event.getCommands().removeAll(removals);
	}
	
	// Probably self-explanatory.
	private int getAllowedAmount(final Player player) {
		if (player.hasPermission("cvreturn.ultimate")) { return RETURN_ULTIMATE; }
		else if (player.hasPermission("cvreturn.extended")) { return RETURN_EXTENDED; }
		else { return RETURN_NORMAL; }
	}
}
