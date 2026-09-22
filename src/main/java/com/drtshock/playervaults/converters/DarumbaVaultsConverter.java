package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DarumbaVaultsConverter implements Converter {
    @Override
    public int run(CommandSender initiator) {
        PlayerVaults plugin = PlayerVaults.getInstance();
        VaultManager vaultManager = VaultManager.getInstance();

        AtomicInteger counter = new AtomicInteger(0);

        Path vaultsPath = PlayerVaults.getInstance().getDataFolder().toPath().resolve("vaults");
        if (Files.exists(vaultsPath)) {
            Pattern pattern = Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})_(\\d+)\\.yml");
            try (Stream<Path> stream = Files.list(vaultsPath)) {
                stream.forEach(p -> {
                    Matcher matcher = pattern.matcher(p.getFileName().toString());
                    if (!matcher.find()) {
                        return;
                    }
                    String uuid = matcher.group(1);
                    int id = Integer.parseInt(matcher.group(2));
                    try {
                        YamlConfiguration conf = YamlConfiguration.loadConfiguration(p.toFile());
                        Inventory inventory = Bukkit.createInventory(null, (6 * 9), "Converting!");
                        for (int i = 0; i < inventory.getSize(); i++) {
                            if (conf.getItemStack("items." + i) instanceof ItemStack is && is.getType() != Material.AIR) {
                                inventory.setItem(i, is);
                            }
                        }
                        vaultManager.saveVault(inventory, uuid, id);
                        counter.incrementAndGet();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to convert vault " + p.getFileName(), e);
                    }
                });
            } catch (IOException ignored) {
            }
            try {
                Files.move(vaultsPath, PlayerVaults.getInstance().getDataFolder().toPath().resolve("oldDarumbaVaults"));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to move old DarumbaVaults file", e);
            }
        }

        return counter.get();
    }

    @Override
    public boolean canConvert() {
        Path vaultsPath = PlayerVaults.getInstance().getDataFolder().toPath().resolve("vaults");
        if (Files.exists(vaultsPath)) {
            try (Stream<Path> stream = Files.list(vaultsPath)) {
                return stream.anyMatch(p -> p.getFileName().toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_\\d+\\.yml"));
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "DarumbaVaults";
    }
}
