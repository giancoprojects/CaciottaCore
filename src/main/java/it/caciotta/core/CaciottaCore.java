package it.caciotta.core;

import it.caciotta.core.afk.*;
import it.caciotta.core.auth.*;
import it.caciotta.core.bag.*;
import it.caciotta.core.broadcast.*;
import it.caciotta.core.command.ReloadCommand;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.help.*;
import it.caciotta.core.home.*;
import it.caciotta.core.rank.*;
import it.caciotta.core.moderation.*;
import it.caciotta.core.news.*;
import it.caciotta.core.scoreboard.*;
import it.caciotta.core.sit.*;
import it.caciotta.core.sleep.*;
import it.caciotta.core.time.*;
import it.caciotta.core.tpa.*;
import it.caciotta.core.tpback.*;
import it.caciotta.core.warp.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Classe principale del plugin CaciottaCore.
 */
public final class CaciottaCore extends JavaPlugin {

    private static CaciottaCore instance;

    private ConfigManager configManager;
    private AuthManager authManager;
    private RankManager rankManager;
    private SitManager sitManager;
    private HomeManager homeManager;
    private WarpManager warpManager;
    private TpaManager tpaManager;
    private TpBackManager tpBackManager;
    private ModerationManager moderationManager;
    private NewsManager newsManager;
    private BagManager bagManager;
    private ScoreboardManager scoreboardManager;
    private SleepManager sleepManager;
    private AfkManager afkManager;
    private TimeManager timeManager;

    public static CaciottaCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Inizializza gestore configurazione
        this.configManager = new ConfigManager(this);

        // Inizializza gestori logica
        this.newsManager = new NewsManager(this);
        this.bagManager = new BagManager(this);
        this.rankManager = new RankManager(this);
        this.sitManager = new SitManager(this);
        this.authManager = new AuthManager(this);
        this.homeManager = new HomeManager(this);
        this.warpManager = new WarpManager(this);
        this.tpaManager = new TpaManager(this);
        this.tpBackManager = new TpBackManager(this);
        this.moderationManager = new ModerationManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.sleepManager = new SleepManager(this);
        this.afkManager = new AfkManager(this);
        this.timeManager = new TimeManager(this);

        // Registrazione Eventi
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new RankListener(this), this);
        pm.registerEvents(new SitListener(this), this);
        pm.registerEvents(new AuthListener(this), this);
        pm.registerEvents(new HomeListener(this), this);
        pm.registerEvents(new WarpListener(this), this);
        pm.registerEvents(new TpaListener(this), this);
        pm.registerEvents(new TpBackListener(this), this);
        pm.registerEvents(new ModerationListener(this), this);
        pm.registerEvents(new ScoreboardListener(this), this);
        pm.registerEvents(new BagListener(this), this);
        pm.registerEvents(new SleepListener(this), this);
        pm.registerEvents(new AfkListener(this), this);

        // Registrazione Comandi Auth
        registerCommand("register", new RegisterCommand(this));
        registerCommand("login", new LoginCommand(this));
        registerCommand("changepassword", new ChangePasswordCommand(this));

        AuthAdminCommand authAdminCommand = new AuthAdminCommand(this);
        PluginCommand adminCmd = getCommand("authadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(authAdminCommand);
            adminCmd.setTabCompleter(authAdminCommand);
        }

        // Registrazione Comandi Home
        registerCommand("sethome", new SetHomeCommand(this));

        HomeCommand homeCommand = new HomeCommand(this);
        PluginCommand hCmd = getCommand("home");
        if (hCmd != null) {
            hCmd.setExecutor(homeCommand);
            hCmd.setTabCompleter(homeCommand);
        }

        DelHomeCommand delHomeCommand = new DelHomeCommand(this);
        PluginCommand delCmd = getCommand("delhome");
        if (delCmd != null) {
            delCmd.setExecutor(delHomeCommand);
            delCmd.setTabCompleter(delHomeCommand);
        }

        registerCommand("homes", new HomesCommand(this));

        // Registrazione Comandi Warp
        WarpsCommand warpsCommand = new WarpsCommand(this);
        PluginCommand warpsCmd = getCommand("warps");
        if (warpsCmd != null) {
            warpsCmd.setExecutor(warpsCommand);
        }

        SetWarpCommand setWarpCommand = new SetWarpCommand(this);
        PluginCommand setWarpCmd = getCommand("setwarp");
        if (setWarpCmd != null) {
            setWarpCmd.setExecutor(setWarpCommand);
            setWarpCmd.setTabCompleter(setWarpCommand);
        }

        DelWarpCommand delWarpCommand = new DelWarpCommand(this);
        PluginCommand delWarpCmd = getCommand("delwarp");
        if (delWarpCmd != null) {
            delWarpCmd.setExecutor(delWarpCommand);
            delWarpCmd.setTabCompleter(delWarpCommand);
        }

        WarpCommand warpCommand = new WarpCommand(this, warpsCommand, setWarpCommand, delWarpCommand);
        PluginCommand warpCmd = getCommand("warp");
        if (warpCmd != null) {
            warpCmd.setExecutor(warpCommand);
            warpCmd.setTabCompleter(warpCommand);
        }

        // Registrazione Comandi TPA
        TpaCommand tpaCommand = new TpaCommand(this);
        PluginCommand tpaCmd = getCommand("tpa");
        if (tpaCmd != null) {
            tpaCmd.setExecutor(tpaCommand);
            tpaCmd.setTabCompleter(tpaCommand);
        }

        TpAcceptCommand tpAcceptCommand = new TpAcceptCommand(this);
        PluginCommand tpAcceptCmd = getCommand("tpaccept");
        if (tpAcceptCmd != null) {
            tpAcceptCmd.setExecutor(tpAcceptCommand);
            tpAcceptCmd.setTabCompleter(tpAcceptCommand);
        }

        TpDenyCommand tpDenyCommand = new TpDenyCommand(this);
        PluginCommand tpDenyCmd = getCommand("tpdeny");
        if (tpDenyCmd != null) {
            tpDenyCmd.setExecutor(tpDenyCommand);
            tpDenyCmd.setTabCompleter(tpDenyCommand);
        }

        registerCommand("tpacancel", new TpCancelCommand(this));

        // Registrazione Comando TPBack
        TpBackCommand tpBackCommand = new TpBackCommand(this);
        PluginCommand tpBackCmd = getCommand("tpback");
        if (tpBackCmd != null) {
            tpBackCmd.setExecutor(tpBackCommand);
            tpBackCmd.setTabCompleter(tpBackCommand);
        }

        // Registrazione Comandi Moderazione
        BanCommand banCommand = new BanCommand(this);
        PluginCommand banCmd = getCommand("ban");
        if (banCmd != null) {
            banCmd.setExecutor(banCommand);
            banCmd.setTabCompleter(banCommand);
        }

        TempBanCommand tempBanCommand = new TempBanCommand(this);
        PluginCommand tempBanCmd = getCommand("tempban");
        if (tempBanCmd != null) {
            tempBanCmd.setExecutor(tempBanCommand);
            tempBanCmd.setTabCompleter(tempBanCommand);
        }

        UnbanCommand unbanCommand = new UnbanCommand(this);
        PluginCommand unbanCmd = getCommand("unban");
        if (unbanCmd != null) {
            unbanCmd.setExecutor(unbanCommand);
            unbanCmd.setTabCompleter(unbanCommand);
        }

        KickCommand kickCommand = new KickCommand(this);
        PluginCommand kickCmd = getCommand("kick");
        if (kickCmd != null) {
            kickCmd.setExecutor(kickCommand);
            kickCmd.setTabCompleter(kickCommand);
        }

        MuteCommand muteCommand = new MuteCommand(this);
        PluginCommand muteCmd = getCommand("mute");
        if (muteCmd != null) {
            muteCmd.setExecutor(muteCommand);
            muteCmd.setTabCompleter(muteCommand);
        }

        TempMuteCommand tempMuteCommand = new TempMuteCommand(this);
        PluginCommand tempMuteCmd = getCommand("tempmute");
        if (tempMuteCmd != null) {
            tempMuteCmd.setExecutor(tempMuteCommand);
            tempMuteCmd.setTabCompleter(tempMuteCommand);
        }

        UnmuteCommand unmuteCommand = new UnmuteCommand(this);
        PluginCommand unmuteCmd = getCommand("unmute");
        if (unmuteCmd != null) {
            unmuteCmd.setExecutor(unmuteCommand);
            unmuteCmd.setTabCompleter(unmuteCommand);
        }

        // Registrazione Comando News
        NewsCommand newsCommand = new NewsCommand(this);
        PluginCommand newsCmd = getCommand("news");
        if (newsCmd != null) {
            newsCmd.setExecutor(newsCommand);
            newsCmd.setTabCompleter(newsCommand);
        }

        // Registrazione Comando Bag
        BagCommand bagCommand = new BagCommand(this);
        PluginCommand bagCmd = getCommand("bag");
        if (bagCmd != null) {
            bagCmd.setExecutor(bagCommand);
            bagCmd.setTabCompleter(bagCommand);
        }

        // Registrazione Comandi Scoreboard
        ScoreboardCommand scoreboardCommand = new ScoreboardCommand(this);
        PluginCommand sbCmd = getCommand("scoreboard");
        if (sbCmd != null) {
            sbCmd.setExecutor(scoreboardCommand);
            sbCmd.setTabCompleter(scoreboardCommand);
        }

        // Registrazione Comandi Rank
        RankCommand rankCommand = new RankCommand(this);
        PluginCommand rankCmd = getCommand("rank");
        if (rankCmd != null) {
            rankCmd.setExecutor(rankCommand);
            rankCmd.setTabCompleter(rankCommand);
        }

        // Registrazione Comando Sit
        SitCommand sitCommand = new SitCommand(this);
        PluginCommand sitCmd = getCommand("sit");
        if (sitCmd != null) {
            sitCmd.setExecutor(sitCommand);
            sitCmd.setTabCompleter(sitCommand);
        }

        // Registrazione Comando Sleep
        SleepCommand sleepCommand = new SleepCommand(this);
        PluginCommand sleepCmd = getCommand("sleep");
        if (sleepCmd != null) {
            sleepCmd.setExecutor(sleepCommand);
            sleepCmd.setTabCompleter(sleepCommand);
        }

        // Registrazione Comando AFK
        AfkCommand afkCommand = new AfkCommand(this);
        PluginCommand afkCmd = getCommand("afk");
        if (afkCmd != null) {
            afkCmd.setExecutor(afkCommand);
            afkCmd.setTabCompleter(afkCommand);
        }

        // Registrazione Comando Broadcast
        BroadcastCommand broadcastCommand = new BroadcastCommand(this);
        PluginCommand bcCmd = getCommand("broadcast");
        if (bcCmd != null) {
            bcCmd.setExecutor(broadcastCommand);
            bcCmd.setTabCompleter(broadcastCommand);
        }

        // Registrazione Comando Orario
        OrarioCommand orarioCommand = new OrarioCommand(this);
        PluginCommand orarioCmd = getCommand("orario");
        if (orarioCmd != null) {
            orarioCmd.setExecutor(orarioCommand);
            orarioCmd.setTabCompleter(orarioCommand);
        }

        // Registrazione Comando Aiuto
        HelpCommand helpCommand = new HelpCommand(this);
        PluginCommand helpCmd = getCommand("aiuto");
        if (helpCmd != null) {
            helpCmd.setExecutor(helpCommand);
            helpCmd.setTabCompleter(helpCommand);
        }

        // Registrazione Comando Reload
        ReloadCommand reloadCommand = new ReloadCommand(this);
        PluginCommand reloadCmd = getCommand("reload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(reloadCommand);
            reloadCmd.setTabCompleter(reloadCommand);
        }

        PluginCommand coreCmd = getCommand("caciottacore");
        if (coreCmd != null) {
            coreCmd.setExecutor(reloadCommand);
            coreCmd.setTabCompleter(reloadCommand);
        }

        getLogger().info("========================================");
        getLogger().info("  CaciottaCore abilitato con successo!  ");
        getLogger().info("  Rank, Sit, BC, Auth, Home, TPA & Board");
        getLogger().info("========================================");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            getLogger().warning("Impossibile registrare il comando /" + name + ": non trovato in plugin.yml");
        }
    }

    /**
     * Ricarica tutte le configurazioni, i dati e i moduli di CaciottaCore.
     */
    public void reloadPlugin() {
        if (configManager != null) {
            configManager.reload();
        }
        if (rankManager != null) {
            rankManager.reload();
        }
        if (scoreboardManager != null) {
            scoreboardManager.reload();
        }
        if (warpManager != null) {
            warpManager.reload();
        }
        if (homeManager != null) {
            homeManager.reload();
        }
        if (authManager != null) {
            authManager.reload();
        }
        if (tpBackManager != null) {
            tpBackManager.reload();
        }
        if (moderationManager != null) {
            moderationManager.reload();
        }
        if (newsManager != null) {
            newsManager.reload();
        }
        if (bagManager != null) {
            bagManager.reload();
        }
        if (sleepManager != null) {
            sleepManager.reload();
        }
        if (afkManager != null) {
            afkManager.reload();
        }
        if (timeManager != null) {
            timeManager.reload();
        }
        if (sitManager != null && configManager != null && !configManager.isSitEnabled()) {
            sitManager.cleanup();
        }
        getLogger().info("Tutte le configurazioni e i moduli di CaciottaCore sono stati ricaricati con successo.");
    }

    @Override
    public void onDisable() {
        if (sitManager != null) {
            sitManager.cleanup();
        }
        if (scoreboardManager != null) {
            scoreboardManager.cleanup();
        }
        if (rankManager != null) {
            rankManager.cleanup();
        }
        if (tpaManager != null) {
            tpaManager.cleanup();
        }
        if (homeManager != null) {
            homeManager.cleanup();
        }
        if (warpManager != null) {
            warpManager.cleanup();
        }
        if (authManager != null) {
            authManager.cleanup();
        }
        if (tpBackManager != null) {
            tpBackManager.cleanup();
        }
        if (moderationManager != null) {
            moderationManager.cleanup();
        }
        if (newsManager != null) {
            newsManager.cleanup();
        }
        if (bagManager != null) {
            bagManager.cleanup();
        }
        if (sleepManager != null) {
            sleepManager.cleanup();
        }
        if (afkManager != null) {
            afkManager.cleanup();
        }
        getLogger().info("CaciottaCore disabilitato con successo.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public SitManager getSitManager() {
        return sitManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public TpBackManager getTpBackManager() {
        return tpBackManager;
    }

    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    public NewsManager getNewsManager() {
        return newsManager;
    }

    public BagManager getBagManager() {
        return bagManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public SleepManager getSleepManager() {
        return sleepManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public TimeManager getTimeManager() {
        return timeManager;
    }
}
