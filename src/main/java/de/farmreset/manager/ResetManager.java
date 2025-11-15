package de.farmreset.manager;

import de.farmreset.FarmReset;
import de.farmreset.models.FarmData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.logging.Logger;

public class ResetManager {

    private final FarmReset plugin;
    private final DataManager dataManager;
    private BukkitTask checkTask;
    private static final java.time.ZoneId TIMEZONE = java.time.ZoneId.of("Europe/Berlin");

    public ResetManager(FarmReset plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        startResetCheck();
    }

    private void startResetCheck() {
        // Prüfe jede Minute, ob ein Reset fällig ist
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndReset();
        }, 0L, 1200L); // 60 Sekunden = 1200 Ticks
    }

    private void checkAndReset() {
        ZonedDateTime now = ZonedDateTime.now(TIMEZONE);
        
        // Lese Config-Werte
        int resetHour = plugin.getConfig().getInt("resetHour", 12);
        int intervalDays = plugin.getConfig().getInt("resetIntervalDays", 30);
        long lastResetTimestamp = plugin.getConfig().getLong("lastReset", 0);
        
        // Wenn noch kein Reset gemacht wurde, setze auf jetzt minus Intervall
        if (lastResetTimestamp == 0) {
            lastResetTimestamp = now.minusDays(intervalDays).toEpochSecond();
            plugin.getConfig().set("lastReset", lastResetTimestamp);
            plugin.saveConfig();
        }
        
        ZonedDateTime lastReset = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochSecond(lastResetTimestamp), 
            TIMEZONE
        );
        
        // Berechne nächsten Reset-Termin: letzter Reset + Intervall-Tage um Reset-Stunde
        ZonedDateTime nextReset = lastReset.plusDays(intervalDays)
            .withHour(resetHour)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
        
        // Wenn der nächste Reset in der Vergangenheit liegt, berechne den nächsten gültigen
        while (nextReset.isBefore(now) || nextReset.isEqual(now)) {
            nextReset = nextReset.plusDays(intervalDays);
        }
        
        // Prüfe ob jetzt der Reset-Zeitpunkt ist (Stunde und Minute passen)
        if (now.getHour() == resetHour && now.getMinute() == 0) {
            // Prüfe ob der berechnete nächste Reset heute ist
            if (nextReset.toLocalDate().equals(now.toLocalDate())) {
                // Prüfe ob wir bereits heute einen Reset gemacht haben (Toleranz: 1 Stunde)
                long today = now.toEpochSecond();
                long lastResetToday = plugin.getConfig().getLong("lastResetToday", 0);
                
                if (lastResetToday < today - 3600) { // 1 Stunde Toleranz
                    performReset();
                    plugin.getConfig().set("lastReset", today);
                    plugin.getConfig().set("lastResetToday", today);
                    plugin.saveConfig();
                }
            }
        }
    }

    private void performReset() {
        Logger logger = plugin.getLogger();
        logger.info("=== Farm Reset wird durchgeführt ===");

        // Benachrichtige alle Spieler
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§c§l=== FARM RESET ===");
            player.sendMessage("§7Die Farm-Welt wird zurückgesetzt...");
        }

        // Hole alle Farmen
        Map<String, FarmData> farms = dataManager.getAllFarms();
        
        if (farms.isEmpty()) {
            logger.warning("Keine Farmen zum Zurücksetzen gefunden!");
            return;
        }

        // Für jede Farm: World löschen und neu erstellen
        for (FarmData farm : farms.values()) {
            resetFarmWorld(farm, logger);
        }

        logger.info("=== Farm Reset abgeschlossen ===");
        
        // Benachrichtige alle Spieler
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§aFarm Reset abgeschlossen! Die Welt wurde zurückgesetzt.");
        }
    }

    private void resetFarmWorld(FarmData farm, Logger logger) {
        Location spawnLocation = farm.getSpawnLocation();
        World world = spawnLocation.getWorld();
        
        if (world == null) {
            logger.warning("Welt für Farm '" + farm.getName() + "' nicht gefunden!");
            return;
        }

        String worldName = world.getName();
        logger.info("Setze Welt '" + worldName + "' zurück...");

        // Entferne alle Spieler aus der Welt
        for (Player player : world.getPlayers()) {
            World defaultWorld = Bukkit.getWorlds().get(0);
            if (defaultWorld != null && !defaultWorld.equals(world)) {
                Location defaultSpawn = defaultWorld.getSpawnLocation();
                player.teleport(defaultSpawn);
                player.sendMessage("§cDu wurdest aus der zurückgesetzten Welt teleportiert!");
            }
        }

        // Entlade die Welt
        Bukkit.unloadWorld(world, false);

        // Lösche die Welt-Dateien
        File worldFolder = world.getWorldFolder();
        if (worldFolder.exists()) {
            deleteDirectory(worldFolder);
            logger.info("Welt-Ordner '" + worldName + "' gelöscht.");
        }

        // Erstelle die Welt neu
        WorldCreator creator = new WorldCreator(worldName);
        World newWorld = Bukkit.createWorld(creator);
        
        if (newWorld != null) {
            logger.info("Welt '" + worldName + "' neu erstellt.");
            
            // Setze den Spawn auf die gespeicherte Position
            Location savedSpawn = farm.getSpawnLocation();
            // Da die Welt neu ist, müssen wir die Koordinaten verwenden
            Location newSpawn = new Location(newWorld, savedSpawn.getX(), savedSpawn.getY(), savedSpawn.getZ(), 
                                            savedSpawn.getYaw(), savedSpawn.getPitch());
            // Stelle sicher, dass die Position sicher ist (Y >= 0)
            if (newSpawn.getY() < 0) {
                newSpawn.setY(newWorld.getHighestBlockYAt(newSpawn) + 1);
            }
            newWorld.setSpawnLocation(newSpawn);
            logger.info("Spawn für Farm '" + farm.getName() + "' auf Position gesetzt: " + 
                       String.format("X: %.1f, Y: %.1f, Z: %.1f", newSpawn.getX(), newSpawn.getY(), newSpawn.getZ()));
        } else {
            logger.severe("Konnte Welt '" + worldName + "' nicht neu erstellen!");
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    public void shutdown() {
        if (checkTask != null && !checkTask.isCancelled()) {
            checkTask.cancel();
        }
    }
}

