package de.farmreset.commands;

import de.farmreset.FarmReset;
import de.farmreset.manager.DataManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FarmCommand implements CommandExecutor {

    private final FarmReset plugin;
    private final DataManager dataManager;

    public FarmCommand(FarmReset plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cDieser Befehl kann nur von Spielern verwendet werden!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1":
                handlePos1(player);
                break;
            case "pos2":
                handlePos2(player);
                break;
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /farm create <Name>");
                    return true;
                }
                handleCreate(player, args[1]);
                break;
            case "reset":
                handleReset(player);
                break;
            case "info":
                handleInfo(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== FarmReset Hilfe ===");
        player.sendMessage("§e/farm pos1 §7- Setze Position 1");
        player.sendMessage("§e/farm pos2 §7- Setze Position 2");
        player.sendMessage("§e/farm create <Name> §7- Erstelle Farm mit Name");
        player.sendMessage("§e/farm reset §7- Setze Farm-Welt zurück (30 Sekunden Countdown)");
        player.sendMessage("§e/farm info §7- Zeige Farm-Informationen");
    }

    private void handlePos1(Player player) {
        Location loc = player.getLocation();
        dataManager.setTempPos1(player.getUniqueId(), loc);
        player.sendMessage("§aPosition 1 gesetzt: §7" + formatLocation(loc));
    }

    private void handlePos2(Player player) {
        Location loc = player.getLocation();
        dataManager.setTempPos2(player.getUniqueId(), loc);
        player.sendMessage("§aPosition 2 gesetzt: §7" + formatLocation(loc));
    }

    private void handleCreate(Player player, String name) {
        Location pos1 = dataManager.getTempPos1(player.getUniqueId());
        Location pos2 = dataManager.getTempPos2(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            player.sendMessage("§cBitte setze zuerst Position 1 und Position 2!");
            return;
        }

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage("§cBeide Positionen müssen in derselben Welt sein!");
            return;
        }

        // Berechne Spawn-Position (Mitte zwischen pos1 und pos2)
        double centerX = (pos1.getX() + pos2.getX()) / 2.0;
        double centerY = (pos1.getY() + pos2.getY()) / 2.0;
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2.0;
        
        Location spawnLocation = new Location(pos1.getWorld(), centerX, centerY, centerZ, player.getLocation().getYaw(), player.getLocation().getPitch());

        dataManager.saveFarm(name, spawnLocation, pos1, pos2);
        player.sendMessage("§aFarm '§e" + name + "§a' erfolgreich erstellt!");
        player.sendMessage("§7Spawn-Position: " + formatLocation(spawnLocation));
    }

    private void handleInfo(Player player) {
        var farms = dataManager.getAllFarms();
        if (farms.isEmpty()) {
            player.sendMessage("§cKeine Farmen gefunden!");
            return;
        }

        player.sendMessage("§6=== Farm-Informationen ===");
        farms.forEach((name, farm) -> {
            player.sendMessage("§e" + name + ":");
            player.sendMessage("  §7Spawn: " + formatLocation(farm.getSpawnLocation()));
            player.sendMessage("  §7Welt: " + farm.getSpawnLocation().getWorld().getName());
        });
    }

    private void handleReset(Player player) {
        // Prüfe ob Spieler in einer Farm-Welt ist
        World playerWorld = player.getWorld();
        String worldName = playerWorld.getName();
        
        // Suche nach einer Farm in dieser Welt
        var farms = dataManager.getAllFarms();
        de.farmreset.models.FarmData farmInWorld = null;
        
        for (de.farmreset.models.FarmData farm : farms.values()) {
            if (farm.getSpawnLocation().getWorld().getName().equals(worldName)) {
                farmInWorld = farm;
                break;
            }
        }
        
        if (farmInWorld == null) {
            player.sendMessage("§cKeine Farm in dieser Welt gefunden!");
            return;
        }
        
        // Starte Reset mit Countdown
        plugin.getResetManager().startManualReset(farmInWorld);
        player.sendMessage("§aFarm Reset gestartet! Die Welt wird in 30 Sekunden zurückgesetzt.");
    }

    private String formatLocation(Location loc) {
        return String.format("X: %.1f, Y: %.1f, Z: %.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}

