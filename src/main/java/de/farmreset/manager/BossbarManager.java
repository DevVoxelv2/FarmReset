package de.farmreset.manager;

import de.farmreset.FarmReset;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BossbarManager {

    private final FarmReset plugin;
    private BossBar bossBar;
    private BukkitTask task;
    private static final ZoneId TIMEZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.GERMAN);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    public BossbarManager(FarmReset plugin) {
        this.plugin = plugin;
        createBossbar();
    }

    private void createBossbar() {
        bossBar = BossBar.bossBar(
            Component.text("Berechne nächsten Reset..."),
            1.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );
    }

    public void startBossbar() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateBossbar();
        }, 0L, 20L); // Jede Sekunde aktualisieren
    }

    public void stopBossbar() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Bossbar für alle Spieler entfernen
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
    }

    private void updateBossbar() {
        ZonedDateTime now = ZonedDateTime.now(TIMEZONE);
        ZonedDateTime nextReset = getNextResetDate(now);

        Duration duration = Duration.between(now, nextReset);
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        // Berechne Progress (0.0 bis 1.0)
        long secondsUntilReset = totalSeconds;
        int intervalDays = plugin.getConfig().getInt("resetIntervalDays", 30);
        long totalSecondsInInterval = intervalDays * 86400L;
        float progress = Math.max(0.0f, Math.min(1.0f, 1.0f - ((float) secondsUntilReset / totalSecondsInInterval)));

        String timeString = String.format("%d Tage, %02d:%02d:%02d", days, hours, minutes, seconds);
        String dateString = nextReset.format(DATE_FORMATTER);
        
        int resetHour = plugin.getConfig().getInt("resetHour", 12);
        String hourString = String.format("%02d:00", resetHour);
        
        Component text = Component.text("§6Farm Reset: §e" + timeString + " §7(" + dateString + " um " + hourString + " Uhr)");
        
        bossBar.name(text);
        bossBar.progress(progress);

        // Bossbar für alle Online-Spieler anzeigen
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    private ZonedDateTime getNextResetDate(ZonedDateTime now) {
        // Lese Config-Werte
        int resetHour = plugin.getConfig().getInt("resetHour", 12);
        int intervalDays = plugin.getConfig().getInt("resetIntervalDays", 30);
        long lastResetTimestamp = plugin.getConfig().getLong("lastReset", 0);
        
        // Wenn noch kein Reset gemacht wurde, berechne basierend auf jetzt
        if (lastResetTimestamp == 0) {
            // Setze nächsten Reset auf heute um die Reset-Stunde
            ZonedDateTime nextReset = now.withHour(resetHour).withMinute(0).withSecond(0).withNano(0);
            // Wenn die Stunde bereits vorbei ist, setze auf morgen
            if (nextReset.isBefore(now) || nextReset.isEqual(now)) {
                nextReset = nextReset.plusDays(1);
            }
            return nextReset;
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
        
        // Wenn der nächste Reset in der Vergangenheit liegt, berechne den nächsten
        while (nextReset.isBefore(now) || nextReset.isEqual(now)) {
            nextReset = nextReset.plusDays(intervalDays);
        }
        
        return nextReset;
    }

    public ZonedDateTime getNextResetDateTime() {
        return getNextResetDate(ZonedDateTime.now(TIMEZONE));
    }
}

