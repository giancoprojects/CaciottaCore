package it.caciotta.core.auth;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

    // Traccia lo stato originale di volo e hovering dei giocatori prima dell'autenticazione
    private final Map<UUID, Boolean> previousAllowFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> previousFlying = new ConcurrentHashMap<>();

    // Timestamp di scadenza per la protezione da caduta post-login
    private final Map<UUID, Long> fallProtectedPlayers = new ConcurrentHashMap<>();

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

    /**
     * Esegue il logout volontario del giocatore:
     * invalida la sessione IP (last_login = 0), chiude eventuali inventari aperti,
     * cancella eventuali teletrasporti pendenti, reimposta stati sit/afk/sleep,
     * riapplica le restrizioni di movimento/volo/effetti, riavvia i promemoria e aggiorna la scoreboard.
     *
     * @param player il giocatore che effettua il logout
     * @return true se il logout ha avuto successo, false se il giocatore non era online o autenticato
     */
    public boolean logout(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (!isAuthenticated(uuid)) {
            return false;
        }

        // Rimuovi dai giocatori autenticati e ripulisci tentativi e protezioni attive
        authenticatedPlayers.remove(uuid);
        failedAttempts.remove(uuid);
        fallProtectedPlayers.remove(uuid);

        // Invalida la sessione IP impostando last_login a 0
        File file = getPlayerFile(uuid);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            yaml.set("last_login", 0L);
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Impossibile aggiornare last_login durante il logout per " + player.getName(), e);
            }
        }

        // Chiudi qualsiasi inventario aperto (es. chest, zaino, cestino, invsee)
        player.closeInventory();

        // Annulla eventuali teletrasporti in sospeso
        if (plugin.getHomeManager() != null) {
            plugin.getHomeManager().cancelPendingTeleport(uuid, false, null);
        }
        if (plugin.getWarpManager() != null) {
            plugin.getWarpManager().cancelPendingTeleport(uuid, false, null);
        }
        if (plugin.getTpaManager() != null) {
            plugin.getTpaManager().cancelPendingTeleport(uuid, false, null);
        }
        if (plugin.getTpBackManager() != null) {
            plugin.getTpBackManager().cancelPendingTeleport(uuid, false, null);
        }
        if (plugin.getRtpManager() != null) {
            plugin.getRtpManager().cancelPendingTeleport(uuid, false, null);
        }

        // Sveglia il giocatore se sta dormendo
        if (plugin.getSleepManager() != null && plugin.getSleepManager().isSleeping(player)) {
            plugin.getSleepManager().wakeUp(player, false);
        }

        // Fai alzare il giocatore se e seduto o rimuovilo da veicoli
        if (plugin.getSitManager() != null && plugin.getSitManager().isSitting(player)) {
            plugin.getSitManager().dismount(player, false);
        } else if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }

        // Esci dalla modalita AFK se attiva
        if (plugin.getAfkManager() != null && plugin.getAfkManager().isAfk(player)) {
            plugin.getAfkManager().removeAfk(player, false);
        }

        // Riapplica le restrizioni (volo temporaneo anticaduta/fly kick, cecita, lentezza)
        applyRestrictions(player);

        // Riavvia i promemoria di login e il task di timeout
        startTasks(player);

        // Aggiorna la scoreboard
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().update(player);
        }

        // Suono di disconnessione
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }

        return true;
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

        // Protezione anticaduta nel vuoto: se il giocatore si trova sotto il livello minimo del mondo, riportalo allo spawn
        if (player.getLocation().getY() < player.getWorld().getMinHeight()) {
            Location spawn = player.getWorld().getSpawnLocation();
            player.teleport(spawn);
        }

        applyRestrictions(player);
        startTasks(player);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        authenticatedPlayers.remove(uuid);
        failedAttempts.remove(uuid);
        cancelTasks(uuid);
        fallProtectedPlayers.remove(uuid);

        // Ripristina lo stato di volo originale del giocatore prima del salvataggio su disco
        Boolean wasAllowFlight = previousAllowFlight.remove(uuid);
        Boolean wasFlying = previousFlying.remove(uuid);
        if (wasAllowFlight != null) {
            player.setAllowFlight(wasAllowFlight);
        }
        if (wasFlying != null && Boolean.TRUE.equals(wasAllowFlight)) {
            player.setFlying(wasFlying);
        } else {
            player.setFlying(false);
        }
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
        UUID uuid = player.getUniqueId();

        // Salva lo stato originale di volo prima dell'applicazione del volo temporaneo di sicurezza
        if (!previousAllowFlight.containsKey(uuid)) {
            previousAllowFlight.put(uuid, player.getAllowFlight());
            previousFlying.put(uuid, player.isFlying());
        }

        // Abilita il volo temporaneo per evitare che il server espella il giocatore per "flying"
        // se si trova a mezz'aria o se non sono presenti blocchi sotto la sua posizione
        if (configManager.isAuthTemporaryFlightEnabled()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }

        player.setFallDistance(0f);

        if (configManager.isBlindnessEffectEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        }
        if (configManager.isSlownessEffectEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 5, false, false, false));
        }
    }

    public void removeRestrictions(Player player) {
        UUID uuid = player.getUniqueId();
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        if (configManager.isAuthTemporaryFlightEnabled()) {
            Boolean wasAllowFlight = previousAllowFlight.remove(uuid);
            Boolean wasFlying = previousFlying.remove(uuid);

            if (wasAllowFlight != null) {
                player.setAllowFlight(wasAllowFlight);
            } else {
                GameMode gm = player.getGameMode();
                player.setAllowFlight(gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR);
            }

            if (wasFlying != null && Boolean.TRUE.equals(wasAllowFlight)) {
                player.setFlying(wasFlying);
            } else {
                player.setFlying(false);
            }
        } else {
            previousAllowFlight.remove(uuid);
            previousFlying.remove(uuid);
        }

        // Azzera la distanza di caduta accumulata durante la sosta
        player.setFallDistance(0f);

        // Attiva la protezione temporanea da danni da caduta
        int protectionSec = configManager.getAuthFallProtectionSeconds();
        if (protectionSec > 0) {
            fallProtectedPlayers.put(uuid, System.currentTimeMillis() + (protectionSec * 1000L));
        }
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

    public boolean hasFallProtection(UUID uuid) {
        Long expiresAt = fallProtectedPlayers.get(uuid);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiresAt) {
            fallProtectedPlayers.remove(uuid);
            return false;
        }
        return true;
    }

    public void removeFallProtection(UUID uuid) {
        fallProtectedPlayers.remove(uuid);
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

        for (UUID uuid : previousAllowFlight.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                Boolean wasAllowFlight = previousAllowFlight.get(uuid);
                p.setAllowFlight(wasAllowFlight != null && wasAllowFlight);
                Boolean wasFlying = previousFlying.get(uuid);
                p.setFlying(wasFlying != null && wasFlying);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
        previousAllowFlight.clear();
        previousFlying.clear();
        fallProtectedPlayers.clear();

        authenticatedPlayers.clear();
        failedAttempts.clear();
    }
}
