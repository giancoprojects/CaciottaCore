package it.caciotta.core.moderation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilità per il parsing e la formattazione di intervalli temporali in lingua italiana.
 */
public final class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private TimeUtil() {
    }

    /**
     * Esegue il parsing di una stringa di durata (es. "30s", "10m", "2h", "1d", "7w") in millisecondi.
     *
     * @param input la stringa della durata
     * @return millisecondi corrispondenti
     * @throws IllegalArgumentException se il formato non è valido
     */
    public static long parseDuration(String input) throws IllegalArgumentException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("La durata non puo essere vuota.");
        }

        Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase().trim());
        long totalMillis = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            long multiplier = switch (unit) {
                case "s", "sec", "secondi", "second" -> 1000L;
                case "m", "min", "minuti", "minute" -> 60_000L;
                case "h", "ore", "ora", "hour" -> 3_600_000L;
                case "d", "g", "giorni", "giorno", "day" -> 86_400_000L;
                case "w", "settimane", "settimana", "week" -> 604_800_000L;
                case "mo", "mesi", "mese", "month" -> 2_592_000_000L; // 30 giorni
                case "y", "anni", "anno", "year" -> 31_536_000_000L; // 365 giorni
                default -> throw new IllegalArgumentException("Unita di tempo sconosciuta: '" + unit + "'");
            };

            totalMillis += amount * multiplier;
        }

        if (!found || totalMillis <= 0) {
            throw new IllegalArgumentException("Formato durata non valido. Esempi: '10m', '2h', '1d', '7d'.");
        }

        return totalMillis;
    }

    /**
     * Formatta una quantità di millisecondi in una stringa leggibile in italiano.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "Scaduto";
        }

        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append(days == 1 ? " giorno" : " giorni");
        }
        if (hours > 0) {
            if (!sb.isEmpty()) sb.append(" e ");
            sb.append(hours).append(hours == 1 ? " ora" : " ore");
        }
        if (minutes > 0 && days == 0) {
            if (!sb.isEmpty()) sb.append(" e ");
            sb.append(minutes).append(minutes == 1 ? " minuto" : " minuti");
        }
        if (seconds > 0 && days == 0 && hours == 0) {
            if (!sb.isEmpty()) sb.append(" e ");
            sb.append(seconds).append(seconds == 1 ? " secondo" : " secondi");
        }

        return sb.isEmpty() ? "meno di un secondo" : sb.toString();
    }
}
