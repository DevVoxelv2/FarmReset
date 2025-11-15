package de.farmreset.manager;

import de.farmreset.FarmReset;
import de.farmreset.models.FarmData;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataManager {

    private final FarmReset plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    private final Map<String, FarmData> farms = new HashMap<>();
    private final Map<UUID, Location> tempPos1 = new HashMap<>();
    private final Map<UUID, Location> tempPos2 = new HashMap<>();

    public DataManager(FarmReset plugin) {
        this.plugin = plugin;
        setupDataFile();
        loadData();
    }

    private void setupDataFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        dataFile = new File(plugin.getDataFolder(), "farms.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Konnte farms.yml nicht erstellen: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void loadData() {
        if (dataConfig.getConfigurationSection("farms") == null) {
            return;
        }

        for (String name : dataConfig.getConfigurationSection("farms").getKeys(false)) {
            String path = "farms." + name;
            
            String worldName = dataConfig.getString(path + ".spawn.world");
            double spawnX = dataConfig.getDouble(path + ".spawn.x");
            double spawnY = dataConfig.getDouble(path + ".spawn.y");
            double spawnZ = dataConfig.getDouble(path + ".spawn.z");
            float yaw = (float) dataConfig.getDouble(path + ".spawn.yaw", 0);
            float pitch = (float) dataConfig.getDouble(path + ".spawn.pitch", 0);

            double pos1X = dataConfig.getDouble(path + ".pos1.x");
            double pos1Y = dataConfig.getDouble(path + ".pos1.y");
            double pos1Z = dataConfig.getDouble(path + ".pos1.z");
            
            double pos2X = dataConfig.getDouble(path + ".pos2.x");
            double pos2Y = dataConfig.getDouble(path + ".pos2.y");
            double pos2Z = dataConfig.getDouble(path + ".pos2.z");

            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Welt '" + worldName + "' für Farm '" + name + "' nicht gefunden!");
                continue;
            }

            Location spawnLocation = new Location(world, spawnX, spawnY, spawnZ, yaw, pitch);
            Location pos1 = new Location(world, pos1X, pos1Y, pos1Z);
            Location pos2 = new Location(world, pos2X, pos2Y, pos2Z);

            farms.put(name, new FarmData(name, spawnLocation, pos1, pos2));
        }

        plugin.getLogger().info("Es wurden " + farms.size() + " Farm(en) geladen.");
    }

    public void saveData() {
        dataConfig.set("farms", null);

        for (FarmData farm : farms.values()) {
            String path = "farms." + farm.getName();
            
            Location spawn = farm.getSpawnLocation();
            dataConfig.set(path + ".spawn.world", spawn.getWorld().getName());
            dataConfig.set(path + ".spawn.x", spawn.getX());
            dataConfig.set(path + ".spawn.y", spawn.getY());
            dataConfig.set(path + ".spawn.z", spawn.getZ());
            dataConfig.set(path + ".spawn.yaw", spawn.getYaw());
            dataConfig.set(path + ".spawn.pitch", spawn.getPitch());

            Location pos1 = farm.getPos1();
            dataConfig.set(path + ".pos1.x", pos1.getX());
            dataConfig.set(path + ".pos1.y", pos1.getY());
            dataConfig.set(path + ".pos1.z", pos1.getZ());

            Location pos2 = farm.getPos2();
            dataConfig.set(path + ".pos2.x", pos2.getX());
            dataConfig.set(path + ".pos2.y", pos2.getY());
            dataConfig.set(path + ".pos2.z", pos2.getZ());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte farms.yml nicht speichern: " + e.getMessage());
        }
    }

    public void setTempPos1(UUID uuid, Location location) {
        tempPos1.put(uuid, location);
    }

    public void setTempPos2(UUID uuid, Location location) {
        tempPos2.put(uuid, location);
    }

    public Location getTempPos1(UUID uuid) {
        return tempPos1.get(uuid);
    }

    public Location getTempPos2(UUID uuid) {
        return tempPos2.get(uuid);
    }

    public void saveFarm(String name, Location spawnLocation, Location pos1, Location pos2) {
        farms.put(name, new FarmData(name, spawnLocation, pos1, pos2));
        saveData();
    }

    public Map<String, FarmData> getAllFarms() {
        return farms;
    }

    public FarmData getFarm(String name) {
        return farms.get(name);
    }
}

