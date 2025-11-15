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
        long secondsInMonth = getSecondsInCurrentMonth(now);
        float progress = Math.max(0.0f, Math.min(1.0f, 1.0f - ((float) secondsUntilReset / secondsInMonth)));

        String timeString = String.format("%d Tage, %02d:%02d:%02d", days, hours, minutes, seconds);
        String dateString = nextReset.format(DATE_FORMATTER);
        
        Component text = Component.text("§6Farm Reset: §e" + timeString + " §7(" + dateString + " um 00:00 Uhr)");
        
        bossBar.name(text);
        bossBar.progress(progress);

        // Bossbar für alle Online-Spieler anzeigen
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    private ZonedDateTime getNextResetDate(ZonedDateTime now) {
        // Nächster 1. des Monats
        int currentDay = now.getDayOfMonth();
        ZonedDateTime nextReset;

        if (currentDay == 1 && now.getHour() == 0 && now.getMinute() == 0 && now.getSecond() < 5) {
            // Wir sind gerade am Reset-Tag, nächster Reset ist nächster Monat
            nextReset = now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (currentDay == 1) {
            // Wir sind am 1., aber nicht um Mitternacht - nächster Reset ist nächster Monat
            nextReset = now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else {
            // Nächster 1. des aktuellen oder nächsten Monats
            nextReset = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            if (nextReset.isBefore(now) || nextReset.isEqual(now)) {
                nextReset = nextReset.plusMonths(1);
            }
        }

        return nextReset;
    }

    private long getSecondsInCurrentMonth(ZonedDateTime date) {
        YearMonth yearMonth = YearMonth.from(date);
        return yearMonth.lengthOfMonth() * 86400L;
    }

    public ZonedDateTime getNextResetDateTime() {
        return getNextResetDate(ZonedDateTime.now(TIMEZONE));
    }
}

