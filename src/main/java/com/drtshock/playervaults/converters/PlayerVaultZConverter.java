/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler, turt2live
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

package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerVaultZConverter implements Converter {
    private static final String PLUGINNAME = "PlayerVaultZ";

    @SuppressWarnings("unchecked")
    @Override
    public int run(CommandSender initiator) {
        PlayerVaults plugin = PlayerVaults.getInstance();
        VaultManager vaultManager = VaultManager.getInstance();

        Plugin pvzPlugin = plugin.getServer().getPluginManager().getPlugin(PLUGINNAME);

        Set<String> uuids = new HashSet<>();

        if (pvzPlugin == null) {
            plugin.getLogger().warning(PLUGINNAME + " not running. Need it to convert.");
            return -1;
        }
        try {
            Class<?> pluginClass = Class.forName("com.rugzy.playervaultz.PlayerVaultZ");
            Class<?> databaseClass = Class.forName("com.rugzy.playervaultz.core.storage.StorageManager");
            Class<?> serializerClass = Class.forName("com.rugzy.playervaultz.core.storage.serialization.ItemSerializer");

            Constructor<?> constructor = serializerClass.getConstructor();
            Object serializer = constructor.newInstance();

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            MethodType typeDeserialize = MethodType.methodType(ItemStack[].class, byte[].class);
            MethodHandle deserialize = lookup.findVirtual(serializerClass, "deserialize", typeDeserialize);

            MethodType typeGetDatabase = MethodType.methodType(databaseClass);
            MethodHandle getDataStorage = lookup.findVirtual(pluginClass, "getStorageManager", typeGetDatabase);

            Object storageManager = getDataStorage.invoke(pvzPlugin);

            Field field = storageManager.getClass().getDeclaredField("dataSource");
            field.setAccessible(true);
            Object hik = field.get(storageManager);
            Object conn = hik.getClass().getDeclaredMethod("getConnection").invoke(hik);

            MethodType typePrepareStatement = MethodType.methodType(PreparedStatement.class, String.class);
            MethodHandle prepareStatement = lookup.findVirtual(conn.getClass(), "prepareStatement", typePrepareStatement);

            Map<String, List<Integer>> map = new HashMap<>();

            try (PreparedStatement statement = (PreparedStatement) prepareStatement.invoke(conn, "SELECT id,owner_uuid,vault_number FROM vaults ORDER BY vault_number ASC")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        String uuid = resultSet.getString("owner_uuid");
                        uuids.add(uuid);
                        map.computeIfAbsent(uuid, k -> new ArrayList<>()).add(id);
                    }
                }
            }

            try (PreparedStatement statement = (PreparedStatement) prepareStatement.invoke(conn, "SELECT vault_id,page_number,items_data FROM vault_pages WHERE vault_id = ? ORDER BY page_number ASC")) {
                for (String uuid : uuids) {
                    int curVaultId = 0;
                    for (int id : map.get(uuid)) {
                        curVaultId++;
                        statement.setInt(1, id);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                ItemStack[] items;

                                try {
                                    items = (ItemStack[]) deserialize.invoke(serializer, resultSet.getBytes("items_data"));
                                } catch (Exception e) {
                                    initiator.getServer().getLogger().log(Level.WARNING, "Failed to load vault " + id + " for " + uuid, e);
                                    continue;
                                }
                                Inventory inventory = Bukkit.createInventory(null, items.length % 9 == 0 ? items.length : (6 * 9), "Converting!");
                                inventory.setContents(items);
                                vaultManager.saveVault(inventory, uuid, curVaultId);
                            }
                        }
                    }
                }
            }

        } catch (Throwable e) {
            initiator.getServer().getLogger().log(Level.SEVERE, "Failed to convert vaults", e);
            return -1;
        }

        return uuids.size();
    }

    @Override
    public boolean canConvert() {
        return Bukkit.getServer().getPluginManager().isPluginEnabled(PLUGINNAME);
    }

    @Override
    public String getName() {
        return PLUGINNAME;
    }
}
