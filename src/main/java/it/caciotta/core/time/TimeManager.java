package it.caciotta.core.time;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Gestore dell'orario del server e del fuso orario italiano per CaciottaCore.
 */
public class TimeManager {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SHORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    public TimeManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public ZoneId getZoneId() {
        return configManager.getZoneId();
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(getZoneId());
    }

    public LocalDate today() {
        return LocalDate.now(getZoneId());
    }

    public LocalTime currentTime() {
        return LocalTime.now(getZoneId());
    }

    public String getFormattedDate() {
        return now().format(DATE_FORMATTER);
    }

    public String getFormattedTime() {
        return now().format(TIME_FORMATTER);
    }

    public String getFormattedShortTime() {
        return now().format(SHORT_TIME_FORMATTER);
    }

    public String getDayOfWeek() {
        String day = now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        if (day == null || day.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(day.charAt(0)) + day.substring(1);
    }

    public String formatTimestamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(getZoneId())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    public void reload() {
        plugin.getLogger().info("TimeManager ricaricato. Fuso orario attivo: " + getZoneId().getId());
    }
}
