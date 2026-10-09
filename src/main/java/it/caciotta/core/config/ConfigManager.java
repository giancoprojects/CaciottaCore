package it.caciotta.core.config;

import it.caciotta.core.CaciottaCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Gestore della configurazione e dei messaggi.
 */
public class ConfigManager {

    private final CaciottaCore plugin;
    private FileConfiguration config;
    private String prefix;

    public ConfigManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.reload();
    }

    /**
     * Sincronizza il file config.yml su disco con la configurazione di default incorporata nel JAR.
     * Aggiunge automaticamente qualsiasi nuova sezione, chiave o messaggio mancante preservando
     * rigorosamente tutti i valori personalizzati già impostati dall'amministratore e i commenti di documentazione.
     */
    public synchronized void updateConfigOnDisk() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        try (InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream == null) {
                return;
            }

            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);

            boolean modified = false;
            for (String key : defConfig.getKeys(true)) {
                if (!defConfig.isConfigurationSection(key)) {
                    if (!currentConfig.contains(key)) {
                        currentConfig.set(key, defConfig.get(key));
                        modified = true;
                    }
                }
                try {
                    List<String> comments = defConfig.getComments(key);
                    if (comments != null && !comments.isEmpty() && currentConfig.getComments(key).isEmpty()) {
                        currentConfig.setComments(key, comments);
                        modified = true;
                    }
                    List<String> inlineComments = defConfig.getInlineComments(key);
                    if (inlineComments != null && !inlineComments.isEmpty() && currentConfig.getInlineComments(key).isEmpty()) {
                        currentConfig.setInlineComments(key, inlineComments);
                        modified = true;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (modified) {
                currentConfig.save(configFile);
                plugin.getLogger().info("Aggiornato automaticamente config.yml con i nuovi parametri e le sezioni mancanti!");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossibile aggiornare automaticamente config.yml: " + e.getMessage(), e);
        }
    }

    public void reload() {
        updateConfigOnDisk();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.prefix = config.getString("prefix", "&8[&6Caciotta&eCore&8]&r ");
    }

    public String getPrefix() {
        return prefix;
    }

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            // Generali
            Map.entry("no-permission", "&cNon hai il permesso di eseguire questo comando."),
            Map.entry("player-only", "&cQuesto comando puo essere eseguito solo da un giocatore."),
            Map.entry("plugin-reloaded", "&aTutte le configurazioni e i moduli di CaciottaCore sono stati ricaricati con successo!"),

            // Sit
            Map.entry("sit.sit-success", "&aTi sei seduto. Premi &eShift &aper alzarti."),
            Map.entry("sit.stand-up", "&7Ti sei alzato in piedi."),
            Map.entry("sit.already-sitting", "&cSei gia seduto! Premi &eShift &cper alzarti."),
            Map.entry("sit.cannot-sit", "&cNon puoi sederti in questa condizione (volo, nuoto o sonno)!"),
            Map.entry("sit.cannot-sit-air", "&cNon puoi sederti a mezz'aria o mentre sei in caduta!"),
            Map.entry("sit.cannot-sit-liquid", "&cNon puoi sederti nell'acqua o nella lava!"),
            Map.entry("sit.occupied", "&cQuesto posto e gia occupato da un altro giocatore!"),
            Map.entry("sit.disabled", "&cIl comando /sit e attualmente disabilitato sul server."),

            // Broadcast
            Map.entry("broadcast.usage", "&cUso: &e/{label} <messaggio> &7(oppure &e/{label} title <testo>&7, &e/{label} actionbar <testo>&7)"),

            // TPA
            Map.entry("tpa.usage", "&cUso corretto: &e/{label} <giocatore>"),
            Map.entry("tpa.cannot-tpa-self", "&cNon puoi inviare una richiesta di teletrasporto a te stesso!"),
            Map.entry("tpa.player-not-found", "&cGiocatore non trovato o non online."),
            Map.entry("tpa.player-not-authenticated", "&cIl giocatore &e{player} &cdeve prima autenticarsi prima di poter ricevere richieste."),
            Map.entry("tpa.request-already-sent", "&cHai gia inviato una richiesta attiva a &e{player}&c. Attendi o usa &e/tpacancel&c."),
            Map.entry("tpa.request-sent", "&aRichiesta di teletrasporto inviata a &e{player}&a! Scadra tra &e{timeout} secondi&a."),
            Map.entry("tpa.request-received", "&e{player} &ati ha inviato una richiesta di teletrasporto!"),
            Map.entry("tpa.request-received-info", "&7Usa i pulsanti o scrivi &a/tpaccept &7oppure &c/tpdeny&7:"),
            Map.entry("tpa.button-accept", "&a&l[ACCETTA]"),
            Map.entry("tpa.button-deny", "&c&l[RIFIUTA]"),
            Map.entry("tpa.button-accept-hover", "&aClicca qui per accettare la richiesta di &e{player}"),
            Map.entry("tpa.button-deny-hover", "&cClicca qui per rifiutare la richiesta di &e{player}"),
            Map.entry("tpa.no-pending-requests", "&cNon hai richieste di teletrasporto in sospeso."),
            Map.entry("tpa.request-expired-sender", "&cLa tua richiesta di teletrasporto per &e{player} &ce scaduta."),
            Map.entry("tpa.request-expired-target", "&cLa richiesta di teletrasporto di &e{player} &ce scaduta."),
            Map.entry("tpa.request-accepted-sender", "&e{player} &aha accettato la tua richiesta di teletrasporto!"),
            Map.entry("tpa.request-accepted-target", "&aHai accettato la richiesta di teletrasporto di &e{player}&a."),
            Map.entry("tpa.request-denied-sender", "&c{player} ha rifiutato la tua richiesta di teletrasporto."),
            Map.entry("tpa.request-denied-target", "&cHai rifiutato la richiesta di teletrasporto di &e{player}&c."),
            Map.entry("tpa.request-cancelled", "&aHai annullato la richiesta di teletrasporto verso &e{player}&a."),
            Map.entry("tpa.no-request-to-cancel", "&cNon hai alcuna richiesta di teletrasporto attiva da annullare."),
            Map.entry("tpa.no-request-from-player", "&cNon hai ricevuto alcuna richiesta di teletrasporto da &e{player}&c."),
            Map.entry("tpa.teleport-countdown", "&7Teletrasporto verso &e{player} &7tra &e{seconds} secondi&7... Non muoverti!"),
            Map.entry("tpa.teleport-cancelled-move", "&cTeletrasporto verso &e{player} &cannullato perche ti sei mosso!"),
            Map.entry("tpa.teleport-cancelled-damage", "&cTeletrasporto verso &e{player} &cannullato perche hai subito danno!"),
            Map.entry("tpa.teleport-success", "&aTeletrasportato con successo da &e{player}&a!"),

            // Warp
            Map.entry("warp.set-success", "&aWarp &e{name} &aimpostato con successo!"),
            Map.entry("warp.updated-success", "&aWarp &e{name} &aaggiornato con successo alla tua posizione attuale!"),
            Map.entry("warp.set-limit-reached", "&cHai raggiunto il limite massimo di warp che puoi creare (&e{limit}&c)."),
            Map.entry("warp.already-exists-by-other", "&cIl warp &e{name} &eesiste gia ed e di proprieta di &f{creator}&c."),
            Map.entry("warp.invalid-name", "&cIl nome del warp puo contenere solo lettere, numeri e trattini (max 16 caratteri)."),
            Map.entry("warp.not-found", "&cIl warp &e{name} &cnon esiste. Usa &e/warps&c per vedere la lista."),
            Map.entry("warp.teleporting-now", "&aTeletrasporto al warp &e{name}&a in corso..."),
            Map.entry("warp.teleport-countdown", "&7Teletrasporto al warp &e{name}&7 tra &e{seconds} secondi&7... Non muoverti!"),
            Map.entry("warp.teleport-cancelled-move", "&cTeletrasporto al warp annullato perche ti sei mosso!"),
            Map.entry("warp.teleport-cancelled-damage", "&cTeletrasporto al warp annullato perche hai subito danno!"),
            Map.entry("warp.deleted", "&aWarp &e{name} &aeliminato con successo."),
            Map.entry("warp.delete-not-owner", "&cNon puoi eliminare il warp &e{name}&c perche non e stato creato da te!"),
            Map.entry("warp.list-header", "&6=== Warp Disponibili ({count}) ==="),
            Map.entry("warp.list-empty", "&7Non e presente alcun warp sul server. Usa &e/setwarp <nome>&7 per crearne uno!"),
            Map.entry("warp.list-entry", "&7- &e{name} &8(&7{world}, {x}, {y}, {z}&8)&7 creato da &f{creator}"),

            // TPBack
            Map.entry("tpback.usage", "&cUso corretto: &e/{label}"),
            Map.entry("tpback.no-death-location", "&cNon hai una posizione di morte valida o recente a cui tornare."),
            Map.entry("tpback.expired", "&cIl tempo limite per tornare al punto di morte e scaduto! Devi usarlo entro &e{seconds} secondi &cdalla morte."),
            Map.entry("tpback.daily-limit-reached", "&cHai raggiunto il limite massimo giornaliero di teletrasporti di ritorno (&e{max}&c). Riprova domani!"),
            Map.entry("tpback.still-dead", "&cDevi prima rinascere prima di poter utilizzare questo comando!"),
            Map.entry("tpback.world-not-loaded", "&cIl mondo in cui sei morto non e attualmente caricato."),
            Map.entry("tpback.teleporting-now", "&aTeletrasporto al punto di morte in corso..."),
            Map.entry("tpback.teleport-countdown", "&7Teletrasporto al punto di morte tra &e{seconds} secondi&7... Non muoverti!"),
            Map.entry("tpback.teleport-cancelled-move", "&cTeletrasporto al punto di morte annullato perche ti sei mosso!"),
            Map.entry("tpback.teleport-cancelled-damage", "&cTeletrasporto al punto di morte annullato perche hai subito danno!"),
            Map.entry("tpback.teleport-success", "&aTeletrasportato con successo al punto di morte! &7(Usi rimasti oggi: &e{remaining}&7/&e{max}&7)"),
            Map.entry("tpback.teleport-success-unlimited", "&aTeletrasportato con successo al punto di morte!"),
            Map.entry("tpback.death-notice", "&7Sei morto! Clicca o scrivi &e/tpback &7entro &e{seconds}s &7per tornare al punto di morte. &8(&e{remaining}&8/&e{max} &7usi rimasti oggi&8)"),

            // Moderazione
            Map.entry("moderation.ban-usage", "&cUso corretto: &e/{label} <giocatore> [motivo]"),
            Map.entry("moderation.ban-success", "&aHai bannato permanentemente &e{target}&a. &7Motivo: &f{reason}"),
            Map.entry("moderation.ban-broadcast", "&c&lBAN &8» &e{target} &7e stato bannato permanentemente da &c{staff}&7! Motivo: &f{reason}"),
            Map.entry("moderation.ban-screen", "&c&lSEI STATO BANNATO DAL SERVER!\n&7Motivo: &f{reason}\n&7Staffer: &e{staff}\n&7Durata: &c{duration}\n&7Scadenza: &c{expires}"),
            Map.entry("moderation.tempban-usage", "&cUso corretto: &e/{label} <giocatore> <durata> [motivo]"),
            Map.entry("moderation.tempban-success", "&aHai bannato &e{target} &aper &e{duration}&a. &7Motivo: &f{reason}"),
            Map.entry("moderation.tempban-broadcast", "&c&lTEMPBAN &8» &e{target} &7e stato bannato da &c{staff} &7per &e{duration}&7! Motivo: &f{reason}"),
            Map.entry("moderation.unban-usage", "&cUso corretto: &e/{label} <giocatore>"),
            Map.entry("moderation.unban-success", "&aHai revocato il ban di &e{target}&a con successo."),
            Map.entry("moderation.unban-broadcast", "&a&lUNBAN &8» &e{target} &7e stato sbannato da &e{staff}&7."),
            Map.entry("moderation.not-banned", "&cIl giocatore &e{target} &cnon risulta attualmente bannato."),
            Map.entry("moderation.kick-usage", "&cUso corretto: &e/{label} <giocatore> [motivo]"),
            Map.entry("moderation.kick-success", "&aHai espulso &e{target}&a dal server. &7Motivo: &f{reason}"),
            Map.entry("moderation.kick-broadcast", "&6&lKICK &8» &e{target} &7e stato cacciato da &e{staff}&7! Motivo: &f{reason}"),
            Map.entry("moderation.kick-screen", "&c&lSEI STATO CACCIATO DAL SERVER!\n&7Staffer: &e{staff}\n&7Motivo: &f{reason}"),
            Map.entry("moderation.mute-usage", "&cUso corretto: &e/{label} <giocatore> [motivo]"),
            Map.entry("moderation.mute-success", "&aHai mutato permanentemente &e{target}&a. &7Motivo: &f{reason}"),
            Map.entry("moderation.mute-broadcast", "&e&lMUTE &8» &e{target} &7e stato mutato permanentemente da &6{staff}&7! Motivo: &f{reason}"),
            Map.entry("moderation.mute-notice-player", "&cSei stato mutato permanentemente da &e{staff}&c. &7Motivo: &f{reason}"),
            Map.entry("moderation.mute-chat-blocked", "&cSei mutato permanentemente! Non puoi scrivere in chat. &7Motivo: &f{reason}"),
            Map.entry("moderation.tempmute-usage", "&cUso corretto: &e/{label} <giocatore> <durata> [motivo]"),
            Map.entry("moderation.tempmute-success", "&aHai mutato &e{target} &aper &e{duration}&a. &7Motivo: &f{reason}"),
            Map.entry("moderation.tempmute-broadcast", "&e&lTEMPMUTE &8» &e{target} &7e stato mutato da &6{staff} &7per &e{duration}&7! Motivo: &f{reason}"),
            Map.entry("moderation.tempmute-notice-player", "&cSei stato mutato per &e{duration} &cda &e{staff}&c. &7Motivo: &f{reason}"),
            Map.entry("moderation.tempmute-chat-blocked", "&cSei attualmente mutato per ancora &e{remaining}&c! &7Motivo: &f{reason}"),
            Map.entry("moderation.unmute-usage", "&cUso corretto: &e/{label} <giocatore>"),
            Map.entry("moderation.unmute-success", "&aHai revocato il mute di &e{target}&a con successo."),
            Map.entry("moderation.unmute-broadcast", "&a&lUNMUTE &8» &e{target} &7e stato smutato da &e{staff}&7."),
            Map.entry("moderation.unmute-notice-player", "&aIl tuo mute e stato revocato! Ora puoi nuovamente scrivere in chat."),
            Map.entry("moderation.not-muted", "&cIl giocatore &e{target} &cnon risulta attualmente mutato."),
            Map.entry("moderation.cannot-punish-self", "&cNon puoi sanzionare te stesso!"),
            Map.entry("moderation.cannot-punish-staff", "&cNon puoi sanzionare un membro dello staff o un utente protetto!"),
            Map.entry("moderation.invalid-duration", "&cDurata specificata non valida: &e{error}"),
            Map.entry("moderation.player-not-found", "&cIl giocatore &e{target} &cnon e online o non e stato trovato."),

            // News
            Map.entry("news.no-news", "&cAl momento non sono presenti novita o aggiornamenti recenti del server."),
            Map.entry("news.reloaded", "&aConfigurazione del libro delle novita ricaricata con successo!"),
            Map.entry("news.usage-admin", "&cUso: &e/news &7(oppure &e/news toggle&7, &e/news reload&7)"),
            Map.entry("news.toggled-to-chat", "&eD'ora in poi all'accesso riceverai una notifica in chat con il link cliccabile anziche aprire il libro."),
            Map.entry("news.toggled-to-book", "&aD'ora in poi il libro delle novita si aprira automaticamente a ogni tuo accesso."),
            Map.entry("news.chat-notice", "&7Ci sono novita recenti sul server! &e&l[CLICCA QUI]&7 per aprirle."),
            Map.entry("news.chat-notice-hover", "&eClicca per aprire il libro delle novita!"),
            Map.entry("news.book-button-hide", "&c&l[Non mostrare all'accesso]"),
            Map.entry("news.book-button-hide-hover", "&7Clicca per ricevere solo un avviso in chat con link all'accesso."),
            Map.entry("news.book-button-show", "&a&l[Riattiva apertura all'accesso]"),
            Map.entry("news.book-button-show-hover", "&7Clicca per riattivare l'apertura automatica del libro all'accesso."),

            // Bag
            Map.entry("bag.open-other", "&aStai visualizzando lo zaino di &e{target}&a."),
            Map.entry("bag.target-not-found", "&cGiocatore &e{target} &cnon trovato o mai entrato nel server."),
            Map.entry("bag.cannot-open-dead", "&cNon puoi aprire lo zaino mentre sei morto!"),
            Map.entry("bag.item-forbidden", "&cNon puoi inserire questo oggetto (&e{item}&c) nello zaino!"),
            Map.entry("bag.disabled", "&cIl sistema dello zaino e attualmente disabilitato."),

            // Sleep
            Map.entry("sleep.broadcast-night", "&6🌙 &eÈ calata la notte! &7Per passare la giornata bisogna riposare e dormire."),
            Map.entry("sleep.broadcast-thunder", "&9⛈ &eIl cielo si e oscurato per un temporale! &7Per passare la giornata bisogna riposare e dormire."),
            Map.entry("sleep.broadcast-button", "&e&l[CLICCA QUI PER DORMIRE]"),
            Map.entry("sleep.broadcast-hover", "&aClicca per metterti a riposare (/sleep)!"),
            Map.entry("sleep.sleep-start", "&aTi sei messo a riposare. Premi &eShift &aoppure digita &e/sleep &aper alzarti."),
            Map.entry("sleep.wake-up", "&7Ti sei svegliato e alzato in piedi."),
            Map.entry("sleep.not-night", "&cNon e ancora ora di dormire! Puoi farlo solo di notte (dalle 18:30) o durante un temporale."),
            Map.entry("sleep.wrong-dimension", "&cPuoi dormire solo nell'Overworld!"),
            Map.entry("sleep.already-vehicle", "&cSei gia all'interno di un veicolo o seduto! Alzati prima di dormire."),
            Map.entry("sleep.cannot-sleep-liquid", "&cNon puoi dormire nell'acqua o nella lava!"),
            Map.entry("sleep.cannot-sleep-air", "&cNon puoi dormire a mezz'aria o mentre sei in caduta!"),
            Map.entry("sleep.monsters-nearby", "&cNon puoi riposare adesso: ci sono mostri ostili nelle vicinanze!"),
            Map.entry("sleep.player-sleeping", " &8» &e{player} &aè andato a dormire! &7({sleeping}/{total})"),
            Map.entry("sleep.player-woke-up", " &8» &e{player} &7si e svegliato."),
            Map.entry("sleep.night-skipped", "&6☀️ &a&lBUONGIORNO! &eLa notte e passata e una nuova giornata ha inizio!"),
            Map.entry("sleep.night-skip-countdown", "&eTutti riposano... Il giorno sorgera tra &6{seconds}s&e."),
            Map.entry("sleep.actionbar-resting", "&7💤 Stai riposando... Premi &eShift &7per alzarti."),
            Map.entry("sleep.disabled", "&cIl sistema del sonno e' attualmente disabilitato."),
            Map.entry("sleep.reloaded", "&aConfigurazione del sonno ricaricata con successo!"),

            // AFK
            Map.entry("afk.enter", "&eSei ora in modalita AFK (Spettatore). Muoviti con la tastiera per uscire."),
            Map.entry("afk.exit", "&aNon sei piu in AFK. Bentornato!"),
            Map.entry("afk.broadcast-enter", " &8» &e{player} &7e' ora in AFK."),
            Map.entry("afk.broadcast-exit", " &8» &e{player} &7non e' piu AFK."),
            Map.entry("afk.blocked-action", "&cSei in modalita AFK! Muoviti con la tastiera per uscire prima di eseguire azioni."),
            Map.entry("afk.cannot-afk-dead", "&cNon puoi andare in AFK mentre sei morto!"),
            Map.entry("afk.target-not-found", "&cGiocatore &e{target} &cnon trovato o offline."),
            Map.entry("afk.list-header", "&6=== Giocatori in AFK ({count}) ==="),
            Map.entry("afk.list-empty", "&7Nessun giocatore e' attualmente in AFK."),
            Map.entry("afk.list-entry", "&7- &e{player} &8(&7da {time}&8)"),
            Map.entry("afk.disabled", "&cIl sistema AFK e' attualmente disabilitato."),
            Map.entry("afk.reloaded", "&aConfigurazione del sistema AFK ricaricata con successo!")
    );

    public String getRawMessage(String path) {
        String defaultMsg = DEFAULT_MESSAGES.getOrDefault(path, "&cMessaggio non trovato: messages." + path);
        return config.getString("messages." + path, defaultMsg);
    }

    public String getPrefixedMessage(String path) {
        return prefix + getRawMessage(path);
    }

    public String getPrefixedMessage(String path, Map<String, String> placeholders) {
        String msg = getPrefixedMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return msg;
    }

    public String getFormatted(String path, Map<String, String> placeholders) {
        String msg = getRawMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return msg;
    }

    public int getTeleportDelay() {
        return config.getInt("home.teleport-delay", 3);
    }

    public boolean isCancelOnMove() {
        return config.getBoolean("home.cancel-on-move", true);
    }

    public boolean isCancelOnDamage() {
        return config.getBoolean("home.cancel-on-damage", true);
    }

    public int getDefaultMaxHomes() {
        return config.getInt("home.default-max-homes", 3);
    }

    public boolean isPlayHomeSound() {
        return config.getBoolean("home.play-sound", true);
    }

    public boolean isSpawnHomeParticles() {
        return config.getBoolean("home.spawn-particles", true);
    }

    public int getMinPasswordLength() {
        return config.getInt("auth.min-password-length", 4);
    }

    public int getMaxPasswordLength() {
        return config.getInt("auth.max-password-length", 32);
    }

    public int getAuthTimeoutSeconds() {
        return config.getInt("auth.timeout-seconds", 60);
    }

    public int getReminderIntervalSeconds() {
        return config.getInt("auth.reminder-interval-seconds", 4);
    }

    public int getMaxLoginAttempts() {
        return config.getInt("auth.max-login-attempts", 3);
    }

    public boolean isBlindnessEffectEnabled() {
        return config.getBoolean("auth.effects.blindness", true);
    }

    public boolean isSlownessEffectEnabled() {
        return config.getBoolean("auth.effects.slowness", true);
    }

    public int getSessionTimeoutMinutes() {
        return config.getInt("auth.session-timeout-minutes", 10);
    }

    public int getTpaTimeoutSeconds() {
        return config.getInt("tpa.timeout-seconds", 60);
    }

    public int getTpaTeleportDelay() {
        return config.getInt("tpa.teleport-delay", 3);
    }

    public boolean isTpaCancelOnMove() {
        return config.getBoolean("tpa.cancel-on-move", true);
    }

    public boolean isTpaCancelOnDamage() {
        return config.getBoolean("tpa.cancel-on-damage", true);
    }

    public boolean isTpaPlaySound() {
        return config.getBoolean("tpa.play-sound", true);
    }

    public boolean isTpaSpawnParticles() {
        return config.getBoolean("tpa.spawn-particles", true);
    }

    public boolean isScoreboardEnabled() {
        return config.getBoolean("scoreboard.enabled", true);
    }

    public int getScoreboardUpdateIntervalTicks() {
        return config.getInt("scoreboard.update-interval-ticks", 20);
    }

    public boolean isScoreboardShowNumbers() {
        return config.getBoolean("scoreboard.show-numbers", false);
    }

    public String getScoreboardTitle() {
        return config.getString("scoreboard.title", "&6&lCACIOTTA &e&lCORE");
    }

    public boolean isScoreboardAnimatedTitle() {
        return config.getBoolean("scoreboard.animated-title.enabled", false);
    }

    public int getScoreboardAnimatedTitleIntervalTicks() {
        return config.getInt("scoreboard.animated-title.interval-ticks", 10);
    }

    public java.util.List<String> getScoreboardAnimatedTitleFrames() {
        return config.getStringList("scoreboard.animated-title.frames");
    }

    public java.util.List<String> getScoreboardLines() {
        return config.getStringList("scoreboard.lines");
    }

    public boolean isSitEnabled() {
        return config.getBoolean("sit.enabled", true);
    }

    public boolean isSitClickToSit() {
        return config.getBoolean("sit.click-to-sit", true);
    }

    public boolean isSitRequireEmptyHand() {
        return config.getBoolean("sit.require-empty-hand", true);
    }

    public boolean isSitUseActionbar() {
        return config.getBoolean("sit.use-actionbar", true);
    }

    public boolean isBroadcastPlaySound() {
        return config.getBoolean("broadcast.play-sound", true);
    }

    public String getBroadcastSound() {
        return config.getString("broadcast.sound", "BLOCK_NOTE_BLOCK_PLING");
    }

    public String getBroadcastPrefix() {
        return config.getString("broadcast.prefix", "&8[&4&lANNUNCIO&8]&r ");
    }

    public boolean isBroadcastFancyBanner() {
        return config.getBoolean("broadcast.fancy-banner", true);
    }

    public String getBroadcastBannerHeader() {
        return config.getString("broadcast.banner-header", "&8&m----------------------------------------");
    }

    public String getBroadcastBannerTitle() {
        return config.getString("broadcast.banner-title", "        &c&l📢 ANNUNCIO DELLO STAFF");
    }

    public String getBroadcastBannerFooter() {
        return config.getString("broadcast.banner-footer", "&8&m----------------------------------------");
    }

    public int getWarpTeleportDelay() {
        return config.getInt("warp.teleport-delay", 3);
    }

    public boolean isWarpCancelOnMove() {
        return config.getBoolean("warp.cancel-on-move", true);
    }

    public boolean isWarpCancelOnDamage() {
        return config.getBoolean("warp.cancel-on-damage", true);
    }

    public boolean isWarpPlaySound() {
        return config.getBoolean("warp.play-sound", true);
    }

    public boolean isWarpSpawnParticles() {
        return config.getBoolean("warp.spawn-particles", true);
    }

    public int getMaxWarpsPerPlayer() {
        return config.getInt("warp.max-warps-per-player", 5);
    }

    public int getTpbackTimeoutSeconds() {
        return config.getInt("tpback.timeout-seconds", 30);
    }

    public int getTpbackMaxDailyUses() {
        return config.getInt("tpback.max-daily-uses", 3);
    }

    public int getTpbackTeleportDelay() {
        return config.getInt("tpback.teleport-delay", 3);
    }

    public boolean isTpbackCancelOnMove() {
        return config.getBoolean("tpback.cancel-on-move", true);
    }

    public boolean isTpbackCancelOnDamage() {
        return config.getBoolean("tpback.cancel-on-damage", true);
    }

    public boolean isTpbackPlaySound() {
        return config.getBoolean("tpback.play-sound", true);
    }

    public boolean isTpbackSpawnParticles() {
        return config.getBoolean("tpback.spawn-particles", true);
    }

    public boolean isTpbackNotifyOnDeath() {
        return config.getBoolean("tpback.notify-on-death", true);
    }

    public boolean isNewsEnabled() {
        return config.getBoolean("news.enabled", true);
    }

    public boolean isNewsOpenOnLogin() {
        return config.getBoolean("news.open-on-login", true);
    }

    public String getNewsBookTitle() {
        return config.getString("news.book-title", "&6Novita Server");
    }

    public String getNewsBookAuthor() {
        return config.getString("news.book-author", "&eCaciottaCore");
    }

    public boolean isNewsPlaySound() {
        return config.getBoolean("news.play-sound", true);
    }

    public java.util.List<String> getNewsPages() {
        return config.getStringList("news.pages");
    }

    public boolean isBagEnabled() {
        return config.getBoolean("bag.enabled", true);
    }

    public int getBagDefaultRows() {
        return config.getInt("bag.default-rows", 3);
    }

    public String getBagTitle() {
        return config.getString("bag.title", "&8Zaino Personale &7({player})");
    }

    public String getBagTitleAdmin() {
        return config.getString("bag.title-admin", "&4Ispezione Zaino &8- &c{player}");
    }

    public boolean isBagPlaySound() {
        return config.getBoolean("bag.play-sound", true);
    }

    public String getBagSoundOpen() {
        return config.getString("bag.sound-open", "ITEM_BUNDLE_DROP_CONTENTS");
    }

    public String getBagSoundClose() {
        return config.getString("bag.sound-close", "ITEM_BUNDLE_REMOVE_ONE");
    }

    public boolean isBagDisallowShulkerBoxes() {
        return config.getBoolean("bag.disallow-shulker-boxes", false);
    }

    public boolean isSleepEnabled() {
        return config.getBoolean("sleep.enabled", true);
    }

    public int getSleepStartTick() {
        return config.getInt("sleep.start-tick", 12542);
    }

    public int getSleepEndTick() {
        return config.getInt("sleep.end-tick", 23460);
    }

    public boolean isSleepBroadcastOnNight() {
        return config.getBoolean("sleep.broadcast-on-night", true);
    }

    public boolean isSleepBroadcastOnThunder() {
        return config.getBoolean("sleep.broadcast-on-thunder", true);
    }

    public boolean isSleepNightSkipEnabled() {
        return config.getBoolean("sleep.night-skip-enabled", true);
    }

    public int getSleepNightSkipDelaySeconds() {
        return config.getInt("sleep.night-skip-delay-seconds", 4);
    }

    public boolean isSleepSinglePlayer() {
        return config.getBoolean("sleep.single-player-sleep", true);
    }

    public boolean isSleepResetPhantom() {
        return config.getBoolean("sleep.reset-phantom-statistic", true);
    }

    public boolean isSleepClearWeather() {
        return config.getBoolean("sleep.clear-weather-on-skip", true);
    }

    public boolean isSleepCheckMonsters() {
        return config.getBoolean("sleep.check-monsters-nearby", true);
    }

    public int getSleepMonsterRadius() {
        return config.getInt("sleep.monster-check-radius", 8);
    }

    public boolean isSleepPlaySound() {
        return config.getBoolean("sleep.play-sound", true);
    }

    public boolean isAfkEnabled() {
        return config.getBoolean("afk.enabled", true);
    }

    public boolean isAfkBroadcast() {
        return config.getBoolean("afk.broadcast", true);
    }

    public boolean isAfkPlaySound() {
        return config.getBoolean("afk.play-sound", true);
    }

    public String getAfkTitle() {
        return config.getString("afk.title", "&c&lSEI AFK");
    }

    public String getAfkSubtitle() {
        return config.getString("afk.subtitle", "&eUna piccola pausa ci vuole");
    }

    public String getAfkTabSuffix() {
        return config.getString("afk.tab-suffix", " &7[&cAFK&7]");
    }
}

