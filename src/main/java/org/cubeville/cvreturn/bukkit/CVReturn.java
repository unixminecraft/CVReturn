/*
 * CVReturn Bukkit plugin for Minecraft
 * Copyright (C) 2020  Matt Ciolkosz (https://github.com/mciolkosz)
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CVReturn extends JavaPlugin implements Listener {
	
	private HashSet<String> amounts;
	private HashMap<UUID, List<Location>> allLocations;
	private HashMap<UUID, Location> ignoredLocations;
	
	@Override
	public void onEnable() {
		
		amounts = new HashSet<String>();
		for(int i = 1; i <= 30; i++) {
			if(i < 10) { amounts.add("0" + String.valueOf(i)); }
			amounts.add(String.valueOf(i));
		}
		
		allLocations = new HashMap<UUID, List<Location>>();
		ignoredLocations = new HashMap<UUID, Location>();
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		
		if(!command.getName().equals("return")) { return false; }
		
		final String extendedUsage = command.getUsage() + " [player]";
		String playerName;
		final int count;
		
		if(sender instanceof Player) {
			if(args.length == 0) {
				playerName = sender.getName();
				count = 1;
			}
			else if(args.length == 1) {
				if(amounts.contains(args[0])) {
					try {
						count = Integer.parseInt(args[0]);
						playerName = sender.getName();
					}
					catch(NumberFormatException e) {
						sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
						getLogger().log(Level.WARNING, "Unable to parse given amount: " + args[0]);
						return true;
					}
				}
				else if(!sender.hasPermission("cvreturn.return.other")) {
					sender.sendMessage(command.getPermissionMessage());
					return true;
				}
				else {
					count = 1;
					playerName = args[0];
				}
			}
			else if(args.length == 2) {
				if(!sender.hasPermission("cvreturn.return.other")) {
					sender.sendMessage(command.getPermissionMessage());
					return true;
				}
				if(amounts.contains(args[0])) {
					try {
						count = Integer.parseInt(args[0]);
						playerName = args[1];
					}
					catch(NumberFormatException e) {
						sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
						getLogger().log(Level.WARNING, "Unable to parse given amount: " + args[0]);
						return true;
					}
				}
				else if(amounts.contains(args[1])) {
					try {
						count = Integer.parseInt(args[1]);
						playerName = args[0];
					}
					catch(NumberFormatException e) {
						sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
						getLogger().log(Level.WARNING, "Unable to parse given amount: " + args[1]);
						return true;
					}
				}
				else {
					sender.sendMessage("§cThe amount specified is player-permission-dependent, but it will always be at most 30.");
					return true;
				}
			}
			else if(sender.hasPermission("cvreturn.return.other")) {
				sender.sendMessage(extendedUsage);
				return true;
			}
			else {
				return false;
			}
		}
		else {
			if(args.length == 0) {
				sender.sendMessage(extendedUsage);
				return true;
			}
			else if(args.length == 1) {
				count = 1;
				playerName = args[0];
			}
			else if(args.length == 2) {
				if(amounts.contains(args[0])) {
					try {
						count = Integer.parseInt(args[0]);
						playerName = args[1];
					}
					catch(NumberFormatException e) {
						sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
						getLogger().log(Level.WARNING, "Unable to parse given amount: " + args[0]);
						return true;
					}
				}
				else if(amounts.contains(args[1])) {
					try {
						count = Integer.parseInt(args[1]);
						playerName = args[0];
					}
					catch(NumberFormatException e) {
						sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
						getLogger().log(Level.WARNING, "Unable to parse given amount: " + args[1]);
						return true;
					}
				}
				else {
					sender.sendMessage("§cThe amount specified is player-permission-dependent, but it will always be at most 30.");
					return true;
				}
			}
			else { 
				sender.sendMessage(extendedUsage);
				return true;
			}
		}
		
		if(count < 1) {
			sender.sendMessage("§cThat shouldn't be possible. Either way, you have to return to at least the previous spot.");
			return true;
		}
		if(count > 30) {
			sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
			getLogger().log(Level.WARNING, "Some error occurred along the way. Please check the logs to see what the arguments for the command were.");
			return true;
		}
		
		if(playerName.equalsIgnoreCase("console")) {
			sender.sendMessage("§cYou may not return the console to it's previous position. That breaks things.");
			return true;
		}
		final boolean samePlayer = sender.getName().equalsIgnoreCase(playerName);
		final Player player = samePlayer ? (Player) sender : getServer().getPlayer(playerName);
		if(player == null) {
			sender.sendMessage("§cPlayer §6" + playerName + " §cnot found.");
			return true;
		}
		playerName = player.getName();
		if(!samePlayer && !player.isOnline()) {
			sender.sendMessage("§6" + playerName + " §cis not online.");
			return true;
		}
		
		final UUID uniqueId = player.getUniqueId();
		final List<Location> locations = allLocations.get(uniqueId);
		if(locations == null || locations.isEmpty()) {
			sender.sendMessage((samePlayer ? "§cYou have " : "§6" + playerName + " §chas ") + "no return history.");
			return true;
		}
		
		if(count > getAmount(player) || count > locations.size()) {
			sender.sendMessage((samePlayer ? "§cYou " : "§6" + playerName + " §c ") + "cannot go back that far.");
			return true;
		}
		
		for(int i = 0; i < count; i++) {
			final Location location = locations.remove(0);
			if(i != count - 1) { continue; }
			if(location == null) {
				sender.sendMessage("§cInternal error, please try again. If the issue persists, please contact a server administrator.");
				getLogger().log(Level.WARNING, "Location number " + i + " was null, no good way to track down the reason why.");
				return true;
			}
			ignoredLocations.put(uniqueId, player.getLocation());
			player.teleport(location.clone().add(0.0D, 0.25D, 0.0D));
		}
		
		player.sendMessage("§a§oZoom!");
		sender.sendMessage((samePlayer ? "§aYou have " : "§6" + playerName + " §ahas ") + "been teleported back §6" + count + " §a" + (count == 1 ? "location" : "locations") + ".");
		return true;
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerTeleport(final PlayerTeleportEvent event) {
		
		final Player player = event.getPlayer();
		final UUID uniqueId = player.getUniqueId();
		List<Location> locations = allLocations.get(uniqueId);
		if(locations == null) { locations = new ArrayList<Location>(); }
		
		final Location from = event.getFrom();
		final Location ignored = ignoredLocations.get(uniqueId);
		if(ignored != null && from.equals(ignored)) {
			ignoredLocations.remove(uniqueId);
			return;
		}
		
		locations.add(0, from);
		int max = getAmount(player);
		int count = 0;
		final Iterator<Location> iter = locations.iterator();
		while(iter.hasNext()) {
			iter.next();
			count++;
			if(count <= max) { continue; }
			iter.remove();
		}
		
		allLocations.put(uniqueId, locations);
	}
	
	private int getAmount(final Player player) {
		if(player.hasPermission("cvreturn.ultimate")) { return 30; }
		else if(player.hasPermission("cvreturn.extended")) { return 15; }
		else { return 10; }
	}
}
