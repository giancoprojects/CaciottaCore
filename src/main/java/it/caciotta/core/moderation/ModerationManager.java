package it.caciotta.core.moderation;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore centrale del modulo di Moderazione:
 * gestione di ban, tempban, unban, kick, mute, tempmute, unmute e persistenza su disco.
 */
public class ModerationManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File dataFile;

    private final Map<UUID, Punishment> activeBans = new ConcurrentHashMap<>();
    private final Map<String, UUID> banNameToId = new ConcurrentHashMap<>();

    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();
    private final Map<String, UUID> muteNameToId = new ConcurrentHashMap<>();

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public ModerationManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataFile = new File(plugin.getDataFolder(), "data/moderation.yml");
        loadData();
    }

    public synchronized void ban(UUID targetId, String targetName, String staffName, String reason) {
        Punishment punishment = new Punishment(targetId, targetName, staffName, PunishmentType.BAN, reason, System.currentTimeMillis(), -1);
        activeBans.put(targetId, punishment);
        banNameToId.put(targetName.toLowerCase(Locale.ROOT), targetId);
        saveData();

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            Component kickComponent = MessageUtil.format(formatBanScreen(punishment));
            target.kick(kickComponent);
        }
    }

    public synchronized void tempBan(UUID targetId, String targetName, String staffName, long durationMillis, String reason) {
        long expiresAt = System.currentTimeMillis() + durationMillis;
        Punishment punishment = new Punishment(targetId, targetName, staffName, PunishmentType.TEMPBAN, reason, System.currentTimeMillis(), expiresAt);
        activeBans.put(targetId, punishment);
        banNameToId.put(targetName.toLowerCase(Locale.ROOT), targetId);
        saveData();

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            Component kickComponent = MessageUtil.format(formatBanScreen(punishment));
            target.kick(kickComponent);
        }
    }

    public synchronized boolean unban(String targetName) {
        UUID uuid = banNameToId.get(targetName.toLowerCase(Locale.ROOT));
        if (uuid == null) {
            // Cerca tramite offline player
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            if (op.hasPlayedBefore() || op.isOnline()) {
                uuid = op.getUniqueId();
            }
        }

        if (uuid == null || !activeBans.containsKey(uuid)) {
            return false;
        }

        activeBans.remove(uuid);
        banNameToId.remove(targetName.toLowerCase(Locale.ROOT));
        saveData();
        return true;
    }

    public void kick(Player target, String staffName, String reason) {
        String screen = formatKickScreen(staffName, reason);
        target.kick(MessageUtil.format(screen));
    }

    public synchronized void mute(UUID targetId, String targetName, String staffName, String reason) {
        Punishment punishment = new Punishment(targetId, targetName, staffName, PunishmentType.MUTE, reason, System.currentTimeMillis(), -1);
        activeMutes.put(targetId, punishment);
        muteNameToId.put(targetName.toLowerCase(Locale.ROOT), targetId);
        saveData();

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("moderation.mute-notice-player", Map.of(
                    "staff", staffName,
                    "reason", punishment.getReason()
            )));
        }
    }

    public synchronized void tempMute(UUID targetId, String targetName, String staffName, long durationMillis, String reason) {
        long expiresAt = System.currentTimeMillis() + durationMillis;
        Punishment punishment = new Punishment(targetId, targetName, staffName, PunishmentType.TEMPMUTE, reason, System.currentTimeMillis(), expiresAt);
        activeMutes.put(targetId, punishment);
        muteNameToId.put(targetName.toLowerCase(Locale.ROOT), targetId);
        saveData();

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("moderation.tempmute-notice-player", Map.of(
                    "staff", staffName,
                    "reason", punishment.getReason(),
                    "duration", punishment.getDurationFormatted()
            )));
        }
    }

    public synchronized boolean unmute(String targetName) {
        UUID uuid = muteNameToId.get(targetName.toLowerCase(Locale.ROOT));
        if (uuid == null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            if (op.hasPlayedBefore() || op.isOnline()) {
                uuid = op.getUniqueId();
            }
        }

        if (uuid == null || !activeMutes.containsKey(uuid)) {
            return false;
        }

        activeMutes.remove(uuid);
        muteNameToId.remove(targetName.toLowerCase(Locale.ROOT));
        saveData();

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("moderation.unmute-notice-player"));
        }
        return true;
    }

    public Punishment getActiveBan(UUID uuid) {
        Punishment ban = activeBans.get(uuid);
        if (ban != null && ban.isExpired()) {
            removeBan(uuid);
            return null;
        }
        return ban;
    }

    public synchronized void removeBan(UUID uuid) {
        Punishment removed = activeBans.remove(uuid);
        if (removed != null) {
            banNameToId.remove(removed.getTargetName().toLowerCase(Locale.ROOT));
            saveData();
        }
    }

    public Punishment getActiveMute(UUID uuid) {
        Punishment mute = activeMutes.get(uuid);
        if (mute != null && mute.isExpired()) {
            removeMute(uuid);
            return null;
        }
        return mute;
    }

    public synchronized void removeMute(UUID uuid) {
        Punishment removed = activeMutes.remove(uuid);
        if (removed != null) {
            muteNameToId.remove(removed.getTargetName().toLowerCase(Locale.ROOT));
            saveData();
        }
    }

    public boolean isBanned(UUID uuid) {
        return getActiveBan(uuid) != null;
    }

    public boolean isMuted(UUID uuid) {
        return getActiveMute(uuid) != null;
    }

    public String formatBanScreen(Punishment ban) {
        String template = configManager.getRawMessage("moderation.ban-screen");
        String expireDate = ban.isPermanent() ? "Permanente" : DATE_FORMAT.format(new Date(ban.getExpiresAt()));
        return template
                .replace("{reason}", ban.getReason())
                .replace("{staff}", ban.getStaffName())
                .replace("{duration}", ban.getDurationFormatted())
                .replace("{remaining}", ban.getRemainingFormatted())
                .replace("{expires}", expireDate);
    }

    public String formatKickScreen(String staffName, String reason) {
        String template = configManager.getRawMessage("moderation.kick-screen");
        return template
                .replace("{staff}", staffName)
                .replace("{reason}", reason != null && !reason.trim().isEmpty() ? reason.trim() : "Nessun motivo specificato");
    }

    public String formatMuteBlocked(Punishment mute) {
        if (mute.isPermanent()) {
            return configManager.getPrefixedMessage("moderation.mute-chat-blocked", Map.of(
                    "reason", mute.getReason()
            ));
        }
        return configManager.getPrefixedMessage("moderation.tempmute-chat-blocked", Map.of(
                "reason", mute.getReason(),
                "remaining", mute.getRemainingFormatted()
        ));
    }

    public void broadcast(String messageKey, Map<String, String> placeholders) {
        String msg = configManager.getPrefixedMessage(messageKey, placeholders);
        Bukkit.broadcast(MessageUtil.format(msg));
    }

    public Set<String> getBannedPlayerNames() {
        return Collections.unmodifiableSet(banNameToId.keySet());
    }

    public Set<String> getMutedPlayerNames() {
        return Collections.unmodifiableSet(muteNameToId.keySet());
    }

    public synchronized void loadData() {
        activeBans.clear();
        banNameToId.clear();
        activeMutes.clear();
        muteNameToId.clear();

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        // Caricamento Ban
        ConfigurationSection bansSection = config.getConfigurationSection("bans");
        if (bansSection != null) {
            for (String key : bansSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String targetName = bansSection.getString(key + ".target-name", "Sconosciuto");
                    String staffName = bansSection.getString(key + ".staff-name", "Console");
                    String typeStr = bansSection.getString(key + ".type", PunishmentType.BAN.name());
                    PunishmentType type = PunishmentType.valueOf(typeStr);
                    String reason = bansSection.getString(key + ".reason", "Nessun motivo specificato");
                    long createdAt = bansSection.getLong(key + ".created-at", System.currentTimeMillis());
                    long expiresAt = bansSection.getLong(key + ".expires-at", -1);

                    Punishment punishment = new Punishment(uuid, targetName, staffName, type, reason, createdAt, expiresAt);
                    if (!punishment.isExpired()) {
                        activeBans.put(uuid, punishment);
                        banNameToId.put(targetName.toLowerCase(Locale.ROOT), uuid);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Impossibile caricare il ban per UUID: " + key);
                }
            }
        }

        // Caricamento Mute
        ConfigurationSection mutesSection = config.getConfigurationSection("mutes");
        if (mutesSection != null) {
            for (String key : mutesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String targetName = mutesSection.getString(key + ".target-name", "Sconosciuto");
                    String staffName = mutesSection.getString(key + ".staff-name", "Console");
                    String typeStr = mutesSection.getString(key + ".type", PunishmentType.MUTE.name());
                    PunishmentType type = PunishmentType.valueOf(typeStr);
                    String reason = mutesSection.getString(key + ".reason", "Nessun motivo specificato");
                    long createdAt = mutesSection.getLong(key + ".created-at", System.currentTimeMillis());
                    long expiresAt = mutesSection.getLong(key + ".expires-at", -1);

                    Punishment punishment = new Punishment(uuid, targetName, staffName, type, reason, createdAt, expiresAt);
                    if (!punishment.isExpired()) {
                        activeMutes.put(uuid, punishment);
                        muteNameToId.put(targetName.toLowerCase(Locale.ROOT), uuid);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Impossibile caricare il mute per UUID: " + key);
                }
            }
        }
    }

    public synchronized void saveData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            YamlConfiguration config = new YamlConfiguration();

            ConfigurationSection bansSection = config.createSection("bans");
            for (Map.Entry<UUID, Punishment> entry : activeBans.entrySet()) {
                Punishment p = entry.getValue();
                if (p.isExpired()) continue;
                String key = entry.getKey().toString();
                bansSection.set(key + ".target-name", p.getTargetName());
                bansSection.set(key + ".staff-name", p.getStaffName());
                bansSection.set(key + ".type", p.getType().name());
                bansSection.set(key + ".reason", p.getReason());
                bansSection.set(key + ".created-at", p.getCreatedAt());
                bansSection.set(key + ".expires-at", p.getExpiresAt());
            }

            ConfigurationSection mutesSection = config.createSection("mutes");
            for (Map.Entry<UUID, Punishment> entry : activeMutes.entrySet()) {
                Punishment p = entry.getValue();
                if (p.isExpired()) continue;
                String key = entry.getKey().toString();
                mutesSection.set(key + ".target-name", p.getTargetName());
                mutesSection.set(key + ".staff-name", p.getStaffName());
                mutesSection.set(key + ".type", p.getType().name());
                mutesSection.set(key + ".reason", p.getReason());
                mutesSection.set(key + ".created-at", p.getCreatedAt());
                mutesSection.set(key + ".expires-at", p.getExpiresAt());
            }

            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore durante il salvataggio dei dati di moderazione in " + dataFile.getName(), e);
        }
    }

    public synchronized void reload() {
        loadData();
        plugin.getLogger().info("ModerationManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        saveData();
    }
}
