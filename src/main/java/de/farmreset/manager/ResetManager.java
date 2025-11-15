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
    private BukkitTask manualResetTask;
    private FarmData currentManualReset;
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

    public void startManualReset(FarmData farm) {
        // Prüfe ob bereits ein Reset läuft
        if (manualResetTask != null && !manualResetTask.isCancelled()) {
            plugin.getLogger().warning("Ein manueller Reset läuft bereits!");
            return;
        }
        
        currentManualReset = farm;
        World world = farm.getSpawnLocation().getWorld();
        String worldName = world != null ? world.getName() : null;
        
        if (world == null || worldName == null) {
            plugin.getLogger().warning("Welt für Farm '" + farm.getName() + "' nicht gefunden!");
            return;
        }
        
        // Sende initiale Nachricht
        sendMessageToWorld(world, "§c§l=== FARM RESET ===");
        sendMessageToWorld(world, "§730 Sekunden bis Reset");
        
        // Countdown-Werte: 30, 20, 10, 5, 4, 3, 2
        int[] countdownSeconds = {30, 20, 10, 5, 4, 3, 2};
        final String finalWorldName = worldName;
        
        for (int seconds : countdownSeconds) {
            final int delay = (30 - seconds) * 20; // Konvertiere Sekunden zu Ticks
            final int sec = seconds;
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World w = Bukkit.getWorld(finalWorldName);
                if (w != null) {
                    sendMessageToWorld(w, "§c" + sec);
                }
            }, delay);
        }
        
        // Führe Reset nach 30 Sekunden durch
        manualResetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Logger logger = plugin.getLogger();
            logger.info("=== Manueller Farm Reset wird durchgeführt ===");
            
            World w = Bukkit.getWorld(finalWorldName);
            if (w != null) {
                sendMessageToWorld(w, "§c§l=== FARM RESET ===");
                sendMessageToWorld(w, "§7Die Farm-Welt wird zurückgesetzt...");
            }
            
            resetFarmWorld(farm, logger);
            
            logger.info("=== Manueller Farm Reset abgeschlossen ===");
            World newWorld = Bukkit.getWorld(finalWorldName);
            if (newWorld != null) {
                sendMessageToWorld(newWorld, "§aFarm Reset abgeschlossen! Die Welt wurde zurückgesetzt.");
            }
            
            currentManualReset = null;
            manualResetTask = null;
        }, 30 * 20); // 30 Sekunden = 600 Ticks
    }
    
    private void sendMessageToWorld(World world, String message) {
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    public void shutdown() {
        if (checkTask != null && !checkTask.isCancelled()) {
            checkTask.cancel();
        }
        if (manualResetTask != null && !manualResetTask.isCancelled()) {
            manualResetTask.cancel();
        }
    }
}

