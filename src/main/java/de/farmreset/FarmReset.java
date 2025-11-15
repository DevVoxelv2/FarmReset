package de.farmreset;

import de.farmreset.commands.FarmCommand;
import de.farmreset.listeners.PlayerJoinListener;
import de.farmreset.manager.BossbarManager;
import de.farmreset.manager.DataManager;
import de.farmreset.manager.ResetManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class FarmReset extends JavaPlugin {

    private static FarmReset instance;
    private DataManager dataManager;
    private BossbarManager bossbarManager;
    private ResetManager resetManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Logger
        Logger logger = getLogger();
        logger.info("FarmReset Plugin wird geladen...");

        // Manager initialisieren
        saveDefaultConfig();
        dataManager = new DataManager(this);
        bossbarManager = new BossbarManager(this);
        resetManager = new ResetManager(this);

        // Commands registrieren
        getCommand("farm").setExecutor(new FarmCommand(this));

        // Listener registrieren
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // Bossbar starten
        bossbarManager.startBossbar();

        logger.info("FarmReset Plugin erfolgreich geladen!");
    }

    @Override
    public void onDisable() {
        if (bossbarManager != null) {
            bossbarManager.stopBossbar();
        }
        if (dataManager != null) {
            dataManager.saveData();
        }
        getLogger().info("FarmReset Plugin wurde deaktiviert!");
    }

    public static FarmReset getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public BossbarManager getBossbarManager() {
        return bossbarManager;
    }

    public ResetManager getResetManager() {
        return resetManager;
    }
}

