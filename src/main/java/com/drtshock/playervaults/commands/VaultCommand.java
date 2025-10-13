/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
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

package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import com.drtshock.playervaults.vaultmanagement.VaultViewInfo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class VaultCommand implements TabExecutor {
    private static final String VAULT_AMOUNT_PERMISSION = "playervaults.amount.";
    private final PlayerVaults plugin;

    public VaultCommand(final PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (VaultOperations.isLocked()) {
            this.plugin.getTL().locked().title().send(sender);
            return true;
        }

        if (sender instanceof final Player player) {
            if (PlayerVaults.getInstance().getInVault().containsKey(player.getUniqueId().toString())) {
                // don't let them open another vault.
                return true;
            }

            switch (args.length) {
                case 1:
                    if (VaultOperations.openOwnVault(player, args[0], true)) {
                        PlayerVaults.getInstance().getInVault().put(player.getUniqueId().toString(), new VaultViewInfo(player.getUniqueId().toString(), Integer.parseInt(args[0])));
                    }
                    break;
                case 2:
                    if (!player.hasPermission(Permission.ADMIN)) {
                        this.plugin.getTL().noPerms().title().send(sender);
                        break;
                    }

                    if ("list".equals(args[1])) {
                        final String target = getTarget(args[0]);
                        final YamlConfiguration file = VaultManager.getInstance().getPlayerVaultFile(target, false);
                        if (file == null) {
                            this.plugin.getTL().vaultDoesNotExist().title().send(sender);
                        } else {
                            final StringBuilder sb = new StringBuilder();
                            for (final String key : file.getKeys(false)) {
                                sb.append(key.replace("vault", "")).append(" ");
                            }

                            this.plugin.getTL().existingVaults().title().with("player", args[0]).with("vault", sb.toString().trim()).send(sender);
                        }
                        break;
                    }

                    final int number;
                    try {
                        number = Integer.parseInt(args[1]);
                    } catch (final NumberFormatException e) {
                        this.plugin.getTL().mustBeNumber().title().send(sender);
                        return true;
                    }

                    final String target = getTarget(args[0]);

                    if (VaultOperations.openOtherVault(player, target, args[1])) {
                        PlayerVaults.getInstance().getInVault().put(player.getUniqueId().toString(), new VaultViewInfo(target, number));
                    } else {
                        this.plugin.getTL().noOwnerFound().title().with("player", args[0]).send(sender);
                    }
                    break;
                default:
                    this.plugin.getTL().help().title().send(sender);
            }
        } else {
            this.plugin.getTL().playerOnly().title().send(sender);
        }

        return true;
    }

    private String getTarget(final String name) {
        final String target;
        OfflinePlayer searchPlayer = Bukkit.getPlayerExact(name);
        if (searchPlayer == null) {
            searchPlayer = Bukkit.getOfflinePlayer(name);
        }
        target = searchPlayer.getUniqueId().toString();
        return target;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String label, final String[] args) {

        if (args.length == 1 && sender instanceof final Player player) {
            return getAvailableVaults(player).stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        return List.of();
    }

    private List<String> getAvailableVaults(final Player player) {
        return player.getEffectivePermissions().stream()
                .map(PermissionAttachmentInfo::getPermission)
                .filter(s -> s.startsWith(VAULT_AMOUNT_PERMISSION))
                .map(s -> {
                    try {
                        return Integer.parseInt(s.substring(VAULT_AMOUNT_PERMISSION.length()));
                    } catch (final NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(i -> i > 0)
                .distinct()
                .map(String::valueOf)
                .toList();
    }
}
