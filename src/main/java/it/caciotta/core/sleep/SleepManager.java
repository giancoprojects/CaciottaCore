package it.caciotta.core.sleep;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestore principale per il sistema /sleep (sonno con e senza letto),
 * ciclo orario vanilla e annuncio globale per passare la giornata.
 */
public class SleepManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    // Giocatori attualmente addormentati (sia via letto vanilla sia via ArmorStand)
    private final Set<UUID> sleepingPlayers = ConcurrentHashMap.newKeySet();

    // Mappa delle entita sedia/letto temporanee (ArmorStand) per chi dorme a terra
    private final Map<UUID, ArmorStand> sleepEntities = new ConcurrentHashMap<>();

    // Posizioni prima di andare a dormire
    private final Map<UUID, Location> preSleepLocations = new ConcurrentHashMap<>();

    // Notifiche gia inviate per mondo e giorno (per non spammare la chat)
    private final Set<String> announcedNights = ConcurrentHashMap.newKeySet();

    // Task attivi di salto notte per ciascun mondo
    private final Map<UUID, BukkitTask> nightSkipTasks = new ConcurrentHashMap<>();

    // Task periodico di controllo orario vanilla
    private BukkitTask timeCheckTask;

    public SleepManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        startTimeCheckTask();
    }

    /**
     * Avvia il task periodico di monitoraggio dell'orario vanilla in tutti i mondi.
     */
    public synchronized void startTimeCheckTask() {
        if (timeCheckTask != null && !timeCheckTask.isCancelled()) {
            timeCheckTask.cancel();
        }

        // Esegue ogni secondo (20 tick)
        timeCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkWorldsTime, 20L, 20L);
    }

    /**
     * Controlla l'orario di ciascun mondo normale (Overworld) e invia l'annuncio
     * globale all'arrivo dell'ora di andare a dormire.
     */
    private void checkWorldsTime() {
        if (!configManager.isSleepEnabled()) {
            return;
        }

        int startTick = configManager.getSleepStartTick();
        int endTick = configManager.getSleepEndTick();

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }

            long time = world.getTime();
            long day = world.getFullTime() / 24000L;
            boolean isNight = (time >= startTick && time <= endTick);
            boolean isThunder = world.isThundering();

            // Pulisci le chiavi di giorni passati
            announcedNights.removeIf(key -> {
                try {
                    String[] parts = key.split(":");
                    if (parts.length >= 2 && parts[0].equals(world.getName())) {
                        long kDay = Long.parseLong(parts[1]);
                        return kDay < day;
                    }
                } catch (Exception ignored) {
                }
                return false;
            });

            // Annuncio per arrivo della notte
            if (isNight && configManager.isSleepBroadcastOnNight()) {
                String nightKey = world.getName() + ":" + day + ":night";
                if (announcedNights.add(nightKey)) {
                    broadcastSleepInvitation(world, false);
                }
            }

            // Annuncio per arrivo di un temporale
            if (isThunder && !isNight && configManager.isSleepBroadcastOnThunder()) {
                String thunderKey = world.getName() + ":" + day + ":thunder";
                if (announcedNights.add(thunderKey)) {
                    broadcastSleepInvitation(world, true);
                }
            }
        }
    }

    /**
     * Manda il messaggio globale a tutti i giocatori del mondo con il pulsante cliccabile.
     */
    public void broadcastSleepInvitation(World world, boolean isThunder) {
        String msgPath = isThunder ? "sleep.broadcast-thunder" : "sleep.broadcast-night";
        String headerText = configManager.getPrefixedMessage(msgPath);
        String buttonText = configManager.getRawMessage("sleep.broadcast-button");
        String hoverText = configManager.getRawMessage("sleep.broadcast-hover");

        Component header = MessageUtil.format(headerText);
        Component button = MessageUtil.format(buttonText)
                .hoverEvent(HoverEvent.showText(MessageUtil.format(hoverText)))
                .clickEvent(ClickEvent.runCommand("/sleep"));

        Component fullNotice = header
                .append(Component.newline())
                .append(Component.text("  "))
                .append(button);

        for (Player player : world.getPlayers()) {
            player.sendMessage(fullNotice);
            if (configManager.isSleepPlaySound()) {
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 1.2f);
            }
        }
    }

    /**
     * Verifica se il giocatore e attualmente addormentato (o sta riposando tramite il plugin).
     */
    public boolean isSleeping(Player player) {
        if (player == null) return false;
        return sleepingPlayers.contains(player.getUniqueId()) || player.isSleeping();
    }

    /**
     * Tenta di far dormire il giocatore (con o senza letto) o di svegliarlo se sta gia dormendo.
     */
    public boolean toggleSleep(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        // Se sta gia riposando, sveglialo
        if (isSleeping(player)) {
            wakeUp(player, true);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.wake-up"));
            return true;
        }

        if (!configManager.isSleepEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.disabled"));
            return false;
        }

        // Controllo autenticazione
        if (plugin.getAuthManager() != null && !plugin.getAuthManager().isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.blocked-action"));
            return false;
        }

        // Controllo dimensione: solo Overworld
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.wrong-dimension"));
            return false;
        }

        // Rispetta l'orario come su Minecraft Vanilla (notte o temporale)
        long time = player.getWorld().getTime();
        int startTick = configManager.getSleepStartTick();
        int endTick = configManager.getSleepEndTick();
        boolean isNight = (time >= startTick && time <= endTick);
        boolean isThunder = player.getWorld().isThundering();

        if (!isNight && !isThunder) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.not-night"));
            return false;
        }

        // Controllo stato giocatore
        if (player.isDead()) {
            return false;
        }

        // Se e seduto con /sit, fallo prima alzare
        if (plugin.getSitManager() != null && plugin.getSitManager().isSitting(player)) {
            plugin.getSitManager().dismount(player, false);
        }

        if (player.isInsideVehicle()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.already-vehicle"));
            return false;
        }

        Location loc = player.getLocation();
        Block blockBelow = loc.getBlock().getRelative(BlockFace.DOWN);

        if (loc.getBlock().isLiquid() || blockBelow.isLiquid()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.cannot-sleep-liquid"));
            return false;
        }

        if (!player.isOnGround() && (blockBelow.isEmpty() || !blockBelow.getType().isSolid())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.cannot-sleep-air"));
            return false;
        }

        // Controllo mostri ostili nelle vicinanze come vanilla
        if (configManager.isSleepCheckMonsters()) {
            int radius = configManager.getSleepMonsterRadius();
            Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, radius, 5, radius, e -> e instanceof Monster);
            if (!nearby.isEmpty()) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.monsters-nearby"));
                return false;
            }
        }

        // 1. Prova se c'e un letto vanilla a portata di mano (entro 3 blocchi)
        Block nearbyBed = findNearbyBed(loc, 3);
        if (nearbyBed != null) {
            try {
                if (player.sleep(nearbyBed.getLocation(), false)) {
                    handleSleepSuccess(player);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Se non c'e un letto fisico, dorme/riposa comunque sul posto senza letto!
        preSleepLocations.put(player.getUniqueId(), loc.clone());

        Location standLoc = loc.clone().subtract(0, 1.7, 0);
        ArmorStand stand = standLoc.getWorld().spawn(standLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setSmall(false);
            as.setMarker(false);
            as.setArms(false);
            as.setBasePlate(false);
            as.setCanTick(false);
            as.setPersistent(false);
            as.setCustomNameVisible(false);
        });

        sleepEntities.put(player.getUniqueId(), stand);
        stand.addPassenger(player);

        handleSleepSuccess(player);
        return true;
    }

    /**
     * Gestisce le azioni comuni quando un giocatore entra con successo nello stato di sonno.
     */
    public void handleSleepSuccess(Player player) {
        UUID uuid = player.getUniqueId();
        sleepingPlayers.add(uuid);

        // Resetta la statistica dei Phantom se abilitato
        if (configManager.isSleepResetPhantom()) {
            player.setStatistic(Statistic.TIME_SINCE_REST, 0);
        }

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.sleep-start"));
        MessageUtil.sendActionBar(player, configManager.getRawMessage("sleep.actionbar-resting"));

        // Annuncio a tutti nel mondo
        World world = player.getWorld();
        int sleeping = getSleepingCount(world);
        int total = getEligiblePlayersCount(world);

        String broadcastMsg = configManager.getPrefixedMessage("sleep.player-sleeping", Map.of(
                "player", player.getName(),
                "sleeping", String.valueOf(sleeping),
                "total", String.valueOf(total)
        ));

        for (Player p : world.getPlayers()) {
            if (!p.equals(player)) {
                MessageUtil.sendMessage(p, broadcastMsg);
            }
        }

        // Controlla se avviare il passaggio della notte
        checkNightSkip(world);
    }

    /**
     * Trova un blocco letto vicino entro il raggio specificato.
     */
    private Block findNearbyBed(Location center, int radius) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (Tag.BEDS.isTagged(b.getType())) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sveglia un giocatore e pulisce le entita associate.
     */
    public void wakeUp(Player player, boolean notifyWorld) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        boolean wasSleeping = sleepingPlayers.remove(uuid);
        ArmorStand stand = sleepEntities.remove(uuid);
        Location preLoc = preSleepLocations.remove(uuid);

        if (stand != null && stand.isValid()) {
            stand.eject();
            stand.remove();
        }

        if (player.isSleeping()) {
            player.wakeup(false);
        }

        if (preLoc != null && player.isOnline() && !player.isDead()) {
            Location safeLoc = preLoc.clone().add(0, 0.1, 0);
            player.teleportAsync(safeLoc);
        }

        if (wasSleeping && notifyWorld && player.isOnline()) {
            World world = player.getWorld();
            int sleeping = getSleepingCount(world);
            if (sleeping == 0) {
                cancelNightSkip(world);
            }

            String wokeMsg = configManager.getPrefixedMessage("sleep.player-woke-up", Map.of("player", player.getName()));
            for (Player p : world.getPlayers()) {
                if (!p.equals(player)) {
                    MessageUtil.sendMessage(p, wokeMsg);
                }
            }
        }
    }

    /**
     * Gestisce l'evento di smontaggio da un'entita sleep.
     */
    public void handleDismount(Player player, Entity vehicle) {
        if (player == null || vehicle == null) return;
        UUID uuid = player.getUniqueId();
        ArmorStand stand = sleepEntities.get(uuid);

        if (stand != null && stand.equals(vehicle)) {
            wakeUp(player, true);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.wake-up"));
        }
    }

    /**
     * Conta quanti giocatori stanno attualmente dormendo in questo mondo.
     */
    public int getSleepingCount(World world) {
        if (world == null) return 0;
        int count = 0;
        for (UUID uuid : sleepingPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getWorld().equals(world)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Conta i giocatori idonei (online in survival o adventure nell'overworld).
     */
    public int getEligiblePlayersCount(World world) {
        if (world == null) return 1;
        int count = 0;
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * Verifica e avvia il timer per far passare la notte se le condizioni sono soddisfatte.
     */
    private void checkNightSkip(World world) {
        if (!configManager.isSleepNightSkipEnabled()) {
            return;
        }

        UUID worldId = world.getUID();
        if (nightSkipTasks.containsKey(worldId)) {
            return; // Task gia in esecuzione
        }

        int sleeping = getSleepingCount(world);
        int eligible = getEligiblePlayersCount(world);

        boolean canSkip = configManager.isSleepSinglePlayer() ? (sleeping >= 1) : (sleeping >= eligible);

        if (!canSkip) {
            return;
        }

        int delaySeconds = configManager.getSleepNightSkipDelaySeconds();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = delaySeconds;

            @Override
            public void run() {
                // Se nessuno sta piu dormendo, annulla il passaggio
                if (getSleepingCount(world) == 0) {
                    cancelNightSkip(world);
                    return;
                }

                if (remaining > 0) {
                    String countdownMsg = configManager.getPrefixedMessage("sleep.night-skip-countdown", Map.of(
                            "seconds", String.valueOf(remaining)
                    ));
                    for (Player p : world.getPlayers()) {
                        MessageUtil.sendActionBar(p, configManager.getRawMessage("sleep.night-skip-countdown")
                                .replace("{seconds}", String.valueOf(remaining)));
                    }
                    remaining--;
                } else {
                    // Esegui il passaggio della notte
                    completeNightSkip(world);
                }
            }
        }, 0L, 20L);

        nightSkipTasks.put(worldId, task);
    }

    /**
     * Annulla il task di salto notte se interrotto prima del tempo.
     */
    public void cancelNightSkip(World world) {
        if (world == null) return;
        BukkitTask task = nightSkipTasks.remove(world.getUID());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Conclude con successo il passaggio della notte e porta il mondo al nuovo giorno.
     */
    private void completeNightSkip(World world) {
        cancelNightSkip(world);

        // Imposta l'ora all'alba (0 tick)
        world.setTime(0L);

        // Pulisce il meteo se configurato
        if (configManager.isSleepClearWeather()) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(24000);
        }

        String morningMsg = configManager.getPrefixedMessage("sleep.night-skipped");

        for (Player p : world.getPlayers()) {
            MessageUtil.sendMessage(p, morningMsg);
            if (configManager.isSleepPlaySound()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            }
        }

        // Sveglia tutti i giocatori addormentati in questo mondo
        for (UUID uuid : Set.copyOf(sleepingPlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.getWorld().equals(world)) {
                wakeUp(p, false);
                if (configManager.isSleepResetPhantom()) {
                    p.setStatistic(Statistic.TIME_SINCE_REST, 0);
                }
            }
        }
    }

    /**
     * Pulisce tutte le entita, sveglia i giocatori e cancella i task attivi.
     */
    public synchronized void cleanup() {
        if (timeCheckTask != null && !timeCheckTask.isCancelled()) {
            timeCheckTask.cancel();
            timeCheckTask = null;
        }

        for (BukkitTask task : nightSkipTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        nightSkipTasks.clear();

        for (Map.Entry<UUID, ArmorStand> entry : sleepEntities.entrySet()) {
            ArmorStand stand = entry.getValue();
            if (stand != null && stand.isValid()) {
                stand.eject();
                stand.remove();
            }
            Player p = Bukkit.getPlayer(entry.getKey());
            Location preLoc = preSleepLocations.get(entry.getKey());
            if (p != null && p.isOnline()) {
                if (p.isSleeping()) {
                    p.wakeup(false);
                }
                if (preLoc != null) {
                    p.teleport(preLoc);
                }
            }
        }

        sleepEntities.clear();
        sleepingPlayers.clear();
        preSleepLocations.clear();
        announcedNights.clear();
    }

    /**
     * Ricarica il gestore del sonno e riavvia il task di controllo orario.
     */
    public synchronized void reload() {
        startTimeCheckTask();
        plugin.getLogger().info("SleepManager ricaricato con successo.");
    }
}
