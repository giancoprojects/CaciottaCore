package it.caciotta.core.auth;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore principale del ciclo di vita dell'autenticazione.
 */
public class AuthManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File authFolder;
    private final File usersMappingFile;
    private YamlConfiguration usersMappingConfig;

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> reminderTasks = new ConcurrentHashMap<>();

    public enum LoginResult {
        SUCCESS,
        WRONG_PASSWORD,
        ALREADY_LOGGED_IN,
        NOT_REGISTERED,
        MAX_ATTEMPTS_EXCEEDED
    }

    public enum RegisterResult {
        SUCCESS,
        ALREADY_LOGGED_IN,
        ALREADY_REGISTERED,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_LONG,
        PASSWORDS_DO_NOT_MATCH
    }

    public AuthManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.authFolder = new File(plugin.getDataFolder(), "data/auth");
        if (!authFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            authFolder.mkdirs();
        }
        this.usersMappingFile = new File(authFolder, "users.yml");
        loadUsersMapping();
    }

    private void loadUsersMapping() {
        if (!usersMappingFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                usersMappingFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossibile creare users.yml", e);
            }
        }
        usersMappingConfig = YamlConfiguration.loadConfiguration(usersMappingFile);
    }

    private void saveUsersMapping() {
        try {
            usersMappingConfig.save(usersMappingFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare users.yml", e);
        }
    }

    public synchronized void reload() {
        loadUsersMapping();
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticatedPlayers.contains(uuid);
    }

    public boolean isAuthenticated(Player player) {
        return player != null && isAuthenticated(player.getUniqueId());
    }

    private File getPlayerFile(UUID uuid) {
        return new File(authFolder, uuid.toString() + ".yml");
    }

    public boolean isRegistered(UUID uuid) {
        return getPlayerFile(uuid).exists();
    }

    public RegisterResult register(Player player, String password, String confirmPassword) {
        UUID uuid = player.getUniqueId();
        if (isAuthenticated(uuid)) {
            return RegisterResult.ALREADY_LOGGED_IN;
        }
        if (isRegistered(uuid)) {
            return RegisterResult.ALREADY_REGISTERED;
        }
        if (!password.equals(confirmPassword)) {
            return RegisterResult.PASSWORDS_DO_NOT_MATCH;
        }
        if (password.length() < configManager.getMinPasswordLength()) {
            return RegisterResult.PASSWORD_TOO_SHORT;
        }
        if (password.length() > configManager.getMaxPasswordLength()) {
            return RegisterResult.PASSWORD_TOO_LONG;
        }

        String salt = AuthSecurity.generateSalt();
        String hash = AuthSecurity.hashPassword(password, salt);

        File file = getPlayerFile(uuid);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        yaml.set("username", player.getName());
        yaml.set("hash", hash);
        yaml.set("salt", salt);
        yaml.set("registered_at", System.currentTimeMillis());
        yaml.set("last_login", System.currentTimeMillis());
        yaml.set("last_ip", Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress());

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore durante il salvataggio dei dati auth per " + player.getName(), e);
            return RegisterResult.PASSWORDS_DO_NOT_MATCH;
        }

        usersMappingConfig.set(player.getName().toLowerCase(Locale.ROOT), uuid.toString());
        saveUsersMapping();

        markAuthenticated(player);
        return RegisterResult.SUCCESS;
    }

    public LoginResult login(Player player, String password) {
        UUID uuid = player.getUniqueId();
        if (isAuthenticated(uuid)) {
            return LoginResult.ALREADY_LOGGED_IN;
        }
        if (!isRegistered(uuid)) {
            return LoginResult.NOT_REGISTERED;
        }

        File file = getPlayerFile(uuid);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String storedHash = yaml.getString("hash");
        String storedSalt = yaml.getString("salt");

        boolean matches = AuthSecurity.verifyPassword(password, storedHash, storedSalt);
        if (!matches) {
            int current = failedAttempts.getOrDefault(uuid, 0) + 1;
            failedAttempts.put(uuid, current);
            if (current >= configManager.getMaxLoginAttempts()) {
                return LoginResult.MAX_ATTEMPTS_EXCEEDED;
            }
            return LoginResult.WRONG_PASSWORD;
        }

        failedAttempts.remove(uuid);
        yaml.set("last_login", System.currentTimeMillis());
        if (player.getAddress() != null) {
            yaml.set("last_ip", player.getAddress().getAddress().getHostAddress());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossibile aggiornare last_login per " + player.getName(), e);
        }

        markAuthenticated(player);
        return LoginResult.SUCCESS;
    }

    public boolean changePassword(Player player, String oldPassword, String newPassword) {
        UUID uuid = player.getUniqueId();
        if (!isRegistered(uuid)) {
            return false;
        }
        File file = getPlayerFile(uuid);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String storedHash = yaml.getString("hash");
        String storedSalt = yaml.getString("salt");

        if (!AuthSecurity.verifyPassword(oldPassword, storedHash, storedSalt)) {
            return false;
        }

        String newSalt = AuthSecurity.generateSalt();
        String newHash = AuthSecurity.hashPassword(newPassword, newSalt);
        yaml.set("hash", newHash);
        yaml.set("salt", newSalt);
        try {
            yaml.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore cambio password per " + player.getName(), e);
            return false;
        }
    }

    public boolean unregister(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (file.exists()) {
            boolean deleted = file.delete();
            authenticatedPlayers.remove(uuid);
            failedAttempts.remove(uuid);
            return deleted;
        }
        return false;
    }

    public boolean unregisterByUsername(String username) {
        String uuidStr = usersMappingConfig.getString(username.toLowerCase(Locale.ROOT));
        if (uuidStr == null) {
            return false;
        }
        UUID uuid = UUID.fromString(uuidStr);
        usersMappingConfig.set(username.toLowerCase(Locale.ROOT), null);
        saveUsersMapping();
        return unregister(uuid);
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        authenticatedPlayers.remove(uuid);
        failedAttempts.remove(uuid);

        // Verifica session timeout IP
        int sessionTimeoutMin = configManager.getSessionTimeoutMinutes();
        if (sessionTimeoutMin > 0 && isRegistered(uuid) && player.getAddress() != null) {
            File file = getPlayerFile(uuid);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            long lastLogin = yaml.getLong("last_login", 0);
            String lastIp = yaml.getString("last_ip", "");
            String currentIp = player.getAddress().getAddress().getHostAddress();

            long elapsedMinutes = (System.currentTimeMillis() - lastLogin) / (1000 * 60);
            if (elapsedMinutes <= sessionTimeoutMin && currentIp.equals(lastIp)) {
                authenticatedPlayers.add(uuid);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.login-success"));

                // Mostra il libro delle novita se disponibile
                if (plugin.getNewsManager() != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && isAuthenticated(player)) {
                            plugin.getNewsManager().openNewsBookIfAvailable(player);
                        }
                    }, 10L);
                }
                return;
            }
        }

        applyRestrictions(player);
        startTasks(player);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        authenticatedPlayers.remove(uuid);
        failedAttempts.remove(uuid);
        cancelTasks(uuid);
    }

    private void markAuthenticated(Player player) {
        UUID uuid = player.getUniqueId();
        authenticatedPlayers.add(uuid);
        failedAttempts.remove(uuid);
        cancelTasks(uuid);
        removeRestrictions(player);

        // Suono di successo
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } catch (Exception ignored) {
        }

        // Rimuovi titoli correnti
        MessageUtil.clearTitle(player);

        // Aggiorna scoreboard
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().update(player);
        }

        // Mostra il libro delle novita se disponibile
        if (plugin.getNewsManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isAuthenticated(player)) {
                    plugin.getNewsManager().openNewsBookIfAvailable(player);
                }
            }, 10L);
        }
    }

    public void applyRestrictions(Player player) {
        if (configManager.isBlindnessEffectEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        }
        if (configManager.isSlownessEffectEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 5, false, false, false));
        }
    }

    public void removeRestrictions(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private void startTasks(Player player) {
        UUID uuid = player.getUniqueId();
        cancelTasks(uuid);

        boolean registered = isRegistered(uuid);

        // Messaggio iniziale
        sendAuthPrompt(player, registered);

        // Promemoria periodico
        int reminderSec = Math.max(2, configManager.getReminderIntervalSeconds());
        BukkitTask reminder = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || isAuthenticated(uuid)) {
                cancelTasks(uuid);
                return;
            }
            sendAuthPrompt(player, isRegistered(uuid));
        }, reminderSec * 20L, reminderSec * 20L);
        reminderTasks.put(uuid, reminder);

        // Timeout kick
        int timeoutSec = configManager.getAuthTimeoutSeconds();
        if (timeoutSec > 0) {
            BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isAuthenticated(uuid)) {
                    player.kick(MessageUtil.format(configManager.getPrefixedMessage("auth.login-timeout")));
                }
            }, timeoutSec * 20L);
            timeoutTasks.put(uuid, timeout);
        }
    }

    private void sendAuthPrompt(Player player, boolean registered) {
        if (registered) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.prompt-login"));
            MessageUtil.sendActionBar(player, configManager.getRawMessage("auth.actionbar-login"));
            MessageUtil.sendTitle(
                    player,
                    configManager.getRawMessage("auth.title-login"),
                    configManager.getRawMessage("auth.subtitle-login"),
                    0, 999999, 0
            );
        } else {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.prompt-register"));
            MessageUtil.sendActionBar(player, configManager.getRawMessage("auth.actionbar-register"));
            MessageUtil.sendTitle(
                    player,
                    configManager.getRawMessage("auth.title-register"),
                    configManager.getRawMessage("auth.subtitle-register"),
                    0, 999999, 0
            );
        }
    }

    private void cancelTasks(UUID uuid) {
        BukkitTask reminder = reminderTasks.remove(uuid);
        if (reminder != null) {
            reminder.cancel();
        }
        BukkitTask timeout = timeoutTasks.remove(uuid);
        if (timeout != null) {
            timeout.cancel();
        }
    }

    public int getRemainingAttempts(UUID uuid) {
        int current = failedAttempts.getOrDefault(uuid, 0);
        return Math.max(0, configManager.getMaxLoginAttempts() - current);
    }

    public void cleanup() {
        for (BukkitTask task : reminderTasks.values()) {
            task.cancel();
        }
        for (BukkitTask task : timeoutTasks.values()) {
            task.cancel();
        }
        reminderTasks.clear();
        timeoutTasks.clear();
        authenticatedPlayers.clear();
        failedAttempts.clear();
    }
}
