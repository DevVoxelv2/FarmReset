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
            player.sendMessage("§7Der Server wird in 2 Sekunden neugestartet...");
        }

        // Hole alle Farmen
        Map<String, FarmData> farms = dataManager.getAllFarms();
        
        if (farms.isEmpty()) {
            logger.warning("Keine Farmen zum Zurücksetzen gefunden!");
            return;
        }

        // Für jede Farm: World löschen und markieren
        for (FarmData farm : farms.values()) {
            deleteFarmWorld(farm, logger);
        }

        // Starte Server-Neustart nach 2 Sekunden
        logger.info("=== Farm Reset - Server wird neugestartet ===");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.shutdown();
        }, 40L); // 2 Sekunden = 40 Ticks
    }
    
    private void deleteFarmWorld(FarmData farm, Logger logger) {
        Location spawnLocation = farm.getSpawnLocation();
        World world = spawnLocation.getWorld();
        
        if (world == null) {
            logger.warning("Welt für Farm '" + farm.getName() + "' nicht gefunden!");
            return;
        }

        String worldName = world.getName();
        logger.info("Lösche Welt '" + worldName + "'...");

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

        // Speichere, dass nach dem Neustart der Spawn gesetzt werden muss
        markFarmForSpawnReset(farm.getName());
    }

    private void resetFarmWorld(FarmData farm, Logger logger) {
        // Lösche die Welt
        deleteFarmWorld(farm, logger);
        
        // Starte Server-Neustart nach 2 Sekunden
        logger.info("Starte Server-Neustart für Farm-Reset...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.shutdown();
        }, 40L); // 2 Sekunden = 40 Ticks
    }
    
    private void markFarmForSpawnReset(String farmName) {
        java.util.List<String> farmsToReset = plugin.getConfig().getStringList("farmsToSetSpawnAfterRestart");
        if (!farmsToReset.contains(farmName)) {
            farmsToReset.add(farmName);
            plugin.getConfig().set("farmsToSetSpawnAfterRestart", farmsToReset);
            plugin.saveConfig();
        }
    }
    
    public void checkAndSetSpawnsAfterRestart() {
        java.util.List<String> farmsToReset = new java.util.ArrayList<>(plugin.getConfig().getStringList("farmsToSetSpawnAfterRestart"));
        
        if (farmsToReset.isEmpty()) {
            return;
        }
        
        Logger logger = plugin.getLogger();
        logger.info("Prüfe ob Spawns nach Neustart gesetzt werden müssen...");
        
        java.util.List<String> processedFarms = new java.util.ArrayList<>();
        
        for (String farmName : farmsToReset) {
            FarmData farm = dataManager.getFarm(farmName);
            if (farm == null) {
                logger.warning("Farm '" + farmName + "' nicht gefunden, entferne aus Reset-Liste!");
                processedFarms.add(farmName);
                continue;
            }
            
            String worldName = farm.getSpawnLocation().getWorld().getName();
            World world = Bukkit.getWorld(worldName);
            
            if (world == null) {
                logger.info("Welt '" + worldName + "' für Farm '" + farmName + "' noch nicht geladen. Erstelle sie...");
                // Erstelle die Welt, falls sie nicht existiert
                WorldCreator creator = new WorldCreator(worldName);
                world = Bukkit.createWorld(creator);
                
                if (world == null) {
                    logger.warning("Konnte Welt '" + worldName + "' nicht erstellen. Versuche es später erneut...");
                    // Warte 5 Sekunden und versuche es erneut
                    final String finalFarmName = farmName;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        setSpawnForFarm(farm);
                        java.util.List<String> remainingFarms = new java.util.ArrayList<>(plugin.getConfig().getStringList("farmsToSetSpawnAfterRestart"));
                        remainingFarms.remove(finalFarmName);
                        plugin.getConfig().set("farmsToSetSpawnAfterRestart", remainingFarms);
                        plugin.saveConfig();
                    }, 100L); // 5 Sekunden = 100 Ticks
                    continue;
                }
            }
            
            setSpawnForFarm(farm);
            processedFarms.add(farmName);
        }
        
        // Entferne alle behandelten Farmen aus der Liste
        farmsToReset.removeAll(processedFarms);
        plugin.getConfig().set("farmsToSetSpawnAfterRestart", farmsToReset);
        plugin.saveConfig();
        
        if (!farmsToReset.isEmpty()) {
            logger.info("Es wurden " + farmsToReset.size() + " Farm(en) für Spawn-Reset markiert (werden später gesetzt).");
        } else {
            logger.info("Alle Spawns wurden erfolgreich gesetzt!");
        }
    }
    
    private void setSpawnForFarm(FarmData farm) {
        Logger logger = plugin.getLogger();
        String worldName = farm.getSpawnLocation().getWorld().getName();
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            logger.warning("Welt '" + worldName + "' für Farm '" + farm.getName() + "' nicht gefunden!");
            return;
        }
        
        // Setze den Spawn auf die gespeicherte Position
        Location savedSpawn = farm.getSpawnLocation();
        Location newSpawn = new Location(world, savedSpawn.getX(), savedSpawn.getY(), savedSpawn.getZ(), 
                                        savedSpawn.getYaw(), savedSpawn.getPitch());
        
        // Stelle sicher, dass die Position sicher ist (Y >= 0)
        if (newSpawn.getY() < 0) {
            newSpawn.setY(world.getHighestBlockYAt(newSpawn) + 1);
        }
        
        world.setSpawnLocation(newSpawn);
        logger.info("Spawn für Farm '" + farm.getName() + "' auf Position gesetzt: " + 
                   String.format("X: %.1f, Y: %.1f, Z: %.1f", newSpawn.getX(), newSpawn.getY(), newSpawn.getZ()));
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
        int[] countdownSeconds = {30, 20, 10, 5, 4, 3, 2, 1};
        final String finalWorldName = worldName;
        
        for (int seconds : countdownSeconds) {
            final int delay = (30 - seconds) * 20; // Konvertiere Sekunden zu Ticks
            final int sec = seconds;
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World w = Bukkit.getWorld(finalWorldName);
                if (w != null) {
                    if (sec == 3) {
                        // Bei 3 Sekunden alle Spieler kicken
                        kickAllPlayersFromWorld(w, "§cFarmReset\n§7Versuche es in 1 Minute wieder");
                    } else {
                        sendMessageToWorld(w, "§c" + sec);
                    }
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
                sendMessageToWorld(w, "§7Der Server wird in 2 Sekunden neugestartet...");
            }
            
            resetFarmWorld(farm, logger);
            
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
    
    private void kickAllPlayersFromWorld(World world, String reason) {
        if (world == null) return;
        
        // Finde eine Standard-Welt zum Teleportieren
        World defaultWorld = Bukkit.getWorlds().get(0);
        Location defaultSpawn = defaultWorld != null ? defaultWorld.getSpawnLocation() : null;
        
        // Kopiere die Spieler-Liste, da wir während der Iteration kicken
        java.util.List<Player> players = new java.util.ArrayList<>(world.getPlayers());
        
        for (Player player : players) {
            // Teleportiere zu Standard-Welt falls möglich
            if (defaultWorld != null && !defaultWorld.equals(world) && defaultSpawn != null) {
                player.teleport(defaultSpawn);
            }
            // Kicke den Spieler
            player.kickPlayer(reason);
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

