package it.caciotta.core.help;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comando /aiuto (alias: /help, /comandi) per mostrare una panoramica interattiva
 * di tutti i comandi disponibili all'utente sul server CaciottaCore.
 */
public class HelpCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;

    public HelpCommand(CaciottaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("caciottacore.admin");

        if (args.length > 0) {
            String section = args[0].toLowerCase(Locale.ROOT);
            showSection(sender, section, isAdmin);
            return true;
        }

        showFullHelp(sender, isAdmin);
        return true;
    }

    private void showFullHelp(CommandSender sender, boolean isAdmin) {
        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
        sender.sendMessage(MessageUtil.format("&6&l🧀 GUIDA COMANDI &e&lCACIOTTACORE"));
        sender.sendMessage(MessageUtil.format("&7Passa il cursore sui comandi per i dettagli o clicca per usarli!"));

        // Categorie navigabili
        Component nav = Component.text("Sezioni: ", NamedTextColor.GRAY)
                .append(createCategoryButton("Home", "home"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Warp", "warp"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("TPA", "tpa"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("TpBack", "tpback"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Sit", "sit"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Scoreboard", "sb"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Rank", "rank"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("News", "news"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Bag", "bag"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Sleep", "sleep"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("AFK", "afk"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(createCategoryButton("Eventi", "eventi"));

        if (isAdmin) {
            nav = nav.append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(createCategoryButton("Admin", "admin"))
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(createCategoryButton("Mod", "mod"));
        }
        sender.sendMessage(nav);
        sender.sendMessage(Component.empty());

        // Sezione Home
        sender.sendMessage(MessageUtil.format("&6🏠 Home &8(/aiuto home)"));
        sender.sendMessage(createCmd("/sethome [nome]", "Imposta una home nella tua posizione attuale", "/sethome ", true));
        sender.sendMessage(createCmd("/home [nome]", "Teletrasportati a una delle tue home", "/home ", true));
        sender.sendMessage(createCmd("/delhome <nome>", "Elimina una tua home esistente", "/delhome ", true));
        sender.sendMessage(createCmd("/homes", "Visualizza l'elenco cliccabile delle tue home", "/homes", false));

        // Sezione Warp
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&6🌀 Warp Pubblici &8(/aiuto warp)"));
        sender.sendMessage(createCmd("/setwarp <nome>", "Crea o aggiorna un punto di teletrasporto pubblico", "/setwarp ", true));
        sender.sendMessage(createCmd("/warp [nome]", "Teletrasportati a un warp o mostra l'elenco", "/warp ", true));
        sender.sendMessage(createCmd("/delwarp <nome>", "Elimina un warp creato da te", "/delwarp ", true));
        sender.sendMessage(createCmd("/warps", "Visualizza l'elenco interattivo di tutti i warp", "/warps", false));

        // Sezione TPA
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&6🌐 Teletrasporto Giocatori &8(/aiuto tpa)"));
        sender.sendMessage(createCmd("/tpa <giocatore>", "Invia una richiesta di teletrasporto a un utente", "/tpa ", true));
        sender.sendMessage(createCmd("/tpaccept [giocatore]", "Accetta una richiesta di teletrasporto in sospeso", "/tpaccept", false));
        sender.sendMessage(createCmd("/tpdeny [giocatore]", "Rifiuta una richiesta di teletrasporto in sospeso", "/tpdeny", false));
        sender.sendMessage(createCmd("/tpacancel", "Annulla la richiesta di teletrasporto inviata", "/tpacancel", false));
        sender.sendMessage(createCmd("/tpback", "Torna al punto della tua ultima morte (entro 30s, max 3/giorno)", "/tpback", false));

        // Sezione Interazione & Scoreboard
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&6🪑 Utilità & Scoreboard"));
        sender.sendMessage(createCmd("/sit", "Siediti sul blocco o alzati in piedi", "/sit", false));
        sender.sendMessage(createCmd("/sleep", "Mettiti a dormire per far passare la notte", "/sleep", false));
        sender.sendMessage(createCmd("/afk", "Entra in modalita AFK (Spettatore)", "/afk", false));
        sender.sendMessage(createCmd("/bag", "Apri il tuo zaino personale portatile", "/bag", false));
        sender.sendMessage(createCmd("/scoreboard", "Attiva o disattiva la tua scoreboard personale", "/scoreboard", false));
        sender.sendMessage(createCmd("/news", "Visualizza il libro con le novita recenti del server", "/news", false));
        sender.sendMessage(createCmd("/orario", "Visualizza la data e l'orario attuale del server", "/orario", false));
        sender.sendMessage(createCmd("/changepassword <vecchia> <nuova>", "Modifica la password del tuo account", "/changepassword ", true));

        // Sezione Rank
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&6👑 Rank &8(/aiuto rank)"));
        sender.sendMessage(createCmd("/rank list", "Mostra l'elenco di tutti i rank disponibili", "/rank list", false));
        sender.sendMessage(createCmd("/rank info [giocatore]", "Visualizza i dettagli del tuo rank o di un altro utente", "/rank info", false));

        // Sezione Eventi a Premi
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&6🏆 Eventi a Premi &8(/aiuto eventi)"));
        sender.sendMessage(createCmd("/evento", "Panoramica dell'evento a premi attivo, obbiettivo e progresso", "/evento", false));
        sender.sendMessage(createCmd("/evento partecipa", "Conferma obbligatoria della partecipazione all'evento", "/evento partecipa", false));
        sender.sendMessage(createCmd("/evento top", "Visualizza la classifica dei partecipanti in tempo reale", "/evento top", false));
        sender.sendMessage(createCmd("/evento info", "Visualizza premi in palio per la Top 3 e regolamento", "/evento info", false));

        // Sezione Admin (se abilitato)
        if (isAdmin) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(MessageUtil.format("&c⚙️ Amministrazione dello Staff &8(/aiuto admin)"));
            sender.sendMessage(createAdminCmd("/broadcast <messaggio>", "Invia un annuncio globale in chat o title", "/broadcast ", true));
            sender.sendMessage(createAdminCmd("/ban <player> [motivo]", "Banna permanentemente un giocatore", "/ban ", true));
            sender.sendMessage(createAdminCmd("/tempban <player> <durata> [motivo]", "Banna temporaneamente un giocatore", "/tempban ", true));
            sender.sendMessage(createAdminCmd("/unban <player>", "Revoca il ban di un utente", "/unban ", true));
            sender.sendMessage(createAdminCmd("/kick <player> [motivo]", "Caccia/espelle un utente dal server", "/kick ", true));
            sender.sendMessage(createAdminCmd("/mute <player> [motivo]", "Muta permanentemente un utente", "/mute ", true));
            sender.sendMessage(createAdminCmd("/tempmute <player> <durata> [motivo]", "Muta temporaneamente un utente", "/tempmute ", true));
            sender.sendMessage(createAdminCmd("/unmute <player>", "Revoca il mute di un utente", "/unmute ", true));
            sender.sendMessage(createAdminCmd("/rank set <player> <rank>", "Assegna un rank a un giocatore", "/rank set ", true));
            sender.sendMessage(createAdminCmd("/rank reset <player>", "Ripristina il rank al valore predefinito", "/rank reset ", true));
            sender.sendMessage(createAdminCmd("/rank default <rank>", "Cambia il rank assegnato ai nuovi utenti", "/rank default ", true));
            sender.sendMessage(createAdminCmd("/rank reload", "Ricarica la configurazione ranks.yml", "/rank reload", false));
            sender.sendMessage(createAdminCmd("/authadmin unregister <player>", "Rimuove la registrazione dell'account di un giocatore", "/authadmin unregister ", true));
            sender.sendMessage(createAdminCmd("/authadmin reload", "Ricarica il file config.yml", "/authadmin reload", false));
            sender.sendMessage(createAdminCmd("/sb reload", "Ricarica la scoreboard per tutti", "/sb reload", false));
            sender.sendMessage(createAdminCmd("/news reload", "Ricarica il libro delle novita", "/news reload", false));
            sender.sendMessage(createAdminCmd("/orario reload", "Ricarica il fuso orario", "/orario reload", false));
            sender.sendMessage(createAdminCmd("/sleep reload", "Ricarica il sistema del sonno", "/sleep reload", false));
            sender.sendMessage(createAdminCmd("/afk <player>", "Imposta lo stato AFK di un utente", "/afk ", true));
            sender.sendMessage(createAdminCmd("/bag <player>", "Ispeziona e gestisci lo zaino di un giocatore", "/bag ", true));
            sender.sendMessage(createAdminCmd("/evento admin <start|stop|kick|setprogress|reset|reload|giveprize>", "Gestione amministrativa degli eventi a premi", "/evento admin ", true));
            sender.sendMessage(createAdminCmd("/reload", "Ricarica tutte le configurazioni e moduli del plugin", "/reload", false));
        }

        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
    }

    private void showSection(CommandSender sender, String section, boolean isAdmin) {
        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));

        switch (section) {
            case "home", "homes" -> {
                sender.sendMessage(MessageUtil.format("&6🏠 Dettagli Comandi Home:"));
                sender.sendMessage(createCmd("/sethome [nome]", "Crea una nuova home dove ti trovi (default: 'home')", "/sethome ", true));
                sender.sendMessage(createCmd("/home [nome]", "Teletrasportati a una delle tue home salvate", "/home ", true));
                sender.sendMessage(createCmd("/delhome <nome>", "Cancella definitivamente una tua home", "/delhome ", true));
                sender.sendMessage(createCmd("/homes", "Mostra la lista delle home con pulsanti cliccabili", "/homes", false));
                sender.sendMessage(MessageUtil.format("&7Suggerimento: puoi impostare home multiple in base al tuo rank!"));
            }
            case "warp", "warps" -> {
                sender.sendMessage(MessageUtil.format("&6🌀 Dettagli Comandi Warp:"));
                sender.sendMessage(createCmd("/setwarp <nome>", "Crea un warp pubblico raggiungibile da chiunque", "/setwarp ", true));
                sender.sendMessage(createCmd("/warp [nome]", "Teletrasportati al warp indicato (o apri la lista)", "/warp ", true));
                sender.sendMessage(createCmd("/delwarp <nome>", "Elimina un warp creato da te", "/delwarp ", true));
                sender.sendMessage(createCmd("/warps", "Mostra tutti i warp pubblici del server con pulsanti", "/warps", false));
                sender.sendMessage(MessageUtil.format("&7Nota: i warp sono pubblici. Solo il creatore o lo staff possono eliminarli."));
            }
            case "tpa", "tp" -> {
                sender.sendMessage(MessageUtil.format("&6🌐 Dettagli Comandi Teletrasporto (TPA):"));
                sender.sendMessage(createCmd("/tpa <giocatore>", "Invia una richiesta per teletrasportarti da un utente", "/tpa ", true));
                sender.sendMessage(createCmd("/tpaccept [giocatore]", "Accetta la richiesta di teletrasporto ricevuta", "/tpaccept", false));
                sender.sendMessage(createCmd("/tpdeny [giocatore]", "Rifiuta la richiesta di teletrasporto", "/tpdeny", false));
                sender.sendMessage(createCmd("/tpacancel", "Annulla la richiesta inviata prima che scada", "/tpacancel", false));
                sender.sendMessage(MessageUtil.format("&7Puoi anche accettare o rifiutare cliccando direttamente sui pulsanti in chat!"));
            }
            case "tpback", "back" -> {
                sender.sendMessage(MessageUtil.format("&6💀 Dettagli Comando Ritorno Morte (/tpback):"));
                sender.sendMessage(createCmd("/tpback", "Teletrasportati dove sei morto (entro 30s, max 3 al giorno)", "/tpback", false));
                sender.sendMessage(MessageUtil.format("&7Nota: consentito solo entro 30 secondi dalla morte e per un massimo di 3 volte durante la giornata."));
            }
            case "sit" -> {
                sender.sendMessage(MessageUtil.format("&6🪑 Dettagli Comando Sit:"));
                sender.sendMessage(createCmd("/sit", "Siediti nella posizione attuale o alzati", "/sit", false));
                sender.sendMessage(MessageUtil.format("&7Suggerimento: puoi sederti anche cliccando con mano vuota su scale o gradini!"));
                sender.sendMessage(MessageUtil.format("&7Per alzarti premi &eShift&7 oppure digita di nuovo &e/sit&7."));
            }
            case "sleep", "dormi", "sonno" -> {
                sender.sendMessage(MessageUtil.format("&6🌙 Dettagli Sistema Sonno:"));
                sender.sendMessage(createCmd("/sleep", "Mettiti a dormire (con o senza letto) per far passare la notte", "/sleep", false));
                sender.sendMessage(createCmd("/sleep status", "Mostra orario del mondo e giocatori addormentati", "/sleep status", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/sleep reload", "Ricarica le impostazioni del ciclo del sonno", "/sleep reload", false));
                }
                sender.sendMessage(MessageUtil.format("&7All'arrivo della notte riceverai un avviso cliccabile in chat!"));
                sender.sendMessage(MessageUtil.format("&7Puoi dormire anche senza letto a terra: premi &eShift &7per alzarti."));
            }
            case "afk", "away" -> {
                sender.sendMessage(MessageUtil.format("&6💤 Dettagli Sistema AFK:"));
                sender.sendMessage(createCmd("/afk", "Entra in modalita AFK (Spettatore) o esci", "/afk", false));
                sender.sendMessage(createCmd("/afk list", "Visualizza l'elenco dei giocatori attualmente in AFK", "/afk list", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/afk <player>", "Attiva o disattiva l'AFK per un altro giocatore", "/afk ", true));
                    sender.sendMessage(createAdminCmd("/afk reload", "Ricarica la configurazione del sistema AFK", "/afk reload", false));
                }
                sender.sendMessage(MessageUtil.format("&7Mentre sei in AFK sei in Spettatore e qualsiasi azione e' bloccata."));
                sender.sendMessage(MessageUtil.format("&7Al minimo movimento con la tastiera uscirai automaticamente dall'AFK!"));
            }
            case "sb", "scoreboard" -> {
                sender.sendMessage(MessageUtil.format("&6📊 Dettagli Scoreboard:"));
                sender.sendMessage(createCmd("/scoreboard", "Attiva o nascondi la scoreboard laterale", "/scoreboard", false));
                sender.sendMessage(createCmd("/sb toggle", "Alterna la visibilita della scoreboard", "/sb toggle", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/sb reload", "Ricarica config.yml e riavvia la scoreboard", "/sb reload", false));
                }
            }
            case "rank", "ranks" -> {
                sender.sendMessage(MessageUtil.format("&6👑 Dettagli Comandi Rank:"));
                sender.sendMessage(createCmd("/rank list", "Visualizza tutti i gradi del server e i loro vantaggi", "/rank list", false));
                sender.sendMessage(createCmd("/rank info [player]", "Mostra prefisso, peso e limiti home del giocatore", "/rank info", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/rank set <player> <rank>", "Assegna un grado a un giocatore", "/rank set ", true));
                    sender.sendMessage(createAdminCmd("/rank reset <player>", "Reimposta il grado predefinito", "/rank reset ", true));
                    sender.sendMessage(createAdminCmd("/rank default <rank>", "Imposta il grado predefinito", "/rank default ", true));
                    sender.sendMessage(createAdminCmd("/rank reload", "Ricarica ranks.yml", "/rank reload", false));
                }
            }
            case "admin", "staff" -> {
                if (!isAdmin) {
                    sender.sendMessage(MessageUtil.format("&cNon hai i permessi per visualizzare i comandi di amministrazione."));
                } else {
                    sender.sendMessage(MessageUtil.format("&c⚙️ Dettagli Comandi Amministrazione Staff:"));
                    sender.sendMessage(createAdminCmd("/broadcast <messaggio>", "Invia un annuncio globale a tutti i giocatori", "/broadcast ", true));
                    sender.sendMessage(createAdminCmd("/broadcast title <titolo>", "Mostra un annuncio a schermo intero (Title)", "/broadcast title ", true));
                    sender.sendMessage(createAdminCmd("/broadcast actionbar <testo>", "Mostra un annuncio nella Actionbar", "/broadcast actionbar ", true));
                    sender.sendMessage(createAdminCmd("/rank set <player> <rank>", "Assegna un rank", "/rank set ", true));
                    sender.sendMessage(createAdminCmd("/rank reset <player>", "Ripristina rank a default", "/rank reset ", true));
                    sender.sendMessage(createAdminCmd("/rank default <rank>", "Imposta il rank predefinito", "/rank default ", true));
                    sender.sendMessage(createAdminCmd("/rank reload", "Ricarica ranks.yml", "/rank reload", false));
                    sender.sendMessage(createAdminCmd("/authadmin unregister <player>", "De-registra account", "/authadmin unregister ", true));
                    sender.sendMessage(createAdminCmd("/authadmin reload", "Ricarica config.yml", "/authadmin reload", false));
                    sender.sendMessage(createAdminCmd("/sb reload", "Ricarica la scoreboard", "/sb reload", false));
                    sender.sendMessage(createAdminCmd("/reload", "Ricarica tutte le configurazioni e i moduli del plugin", "/reload", false));
                }
            }
            case "mod", "moderazione" -> {
                if (!isAdmin) {
                    sender.sendMessage(MessageUtil.format("&cNon hai i permessi per visualizzare i comandi di moderazione."));
                } else {
                    sender.sendMessage(MessageUtil.format("&c🛡️ Dettagli Comandi Moderazione Staff:"));
                    sender.sendMessage(createAdminCmd("/ban <player> [motivo]", "Banna permanentemente un giocatore dal server", "/ban ", true));
                    sender.sendMessage(createAdminCmd("/tempban <player> <durata> [motivo]", "Banna temporaneamente (es: 10m, 2h, 1d)", "/tempban ", true));
                    sender.sendMessage(createAdminCmd("/unban <player>", "Revoca il ban attivo di un giocatore", "/unban ", true));
                    sender.sendMessage(createAdminCmd("/kick <player> [motivo]", "Caccia/espelle immediatamente un giocatore online", "/kick ", true));
                    sender.sendMessage(createAdminCmd("/mute <player> [motivo]", "Muta permanentemente un giocatore in chat", "/mute ", true));
                    sender.sendMessage(createAdminCmd("/tempmute <player> <durata> [motivo]", "Muta temporaneamente (es: 15m, 1h, 1d)", "/tempmute ", true));
                    sender.sendMessage(createAdminCmd("/unmute <player>", "Revoca il mute di un giocatore", "/unmute ", true));
                    sender.sendMessage(MessageUtil.format("&7Durate supportate: &es &7(secondi), &em &7(minuti), &eh &7(ore), &ed &7(giorni), &ew &7(settimane)."));
                }
            }
            case "news", "novita", "novità" -> {
                sender.sendMessage(MessageUtil.format("&6📰 Dettagli Novita del Server:"));
                sender.sendMessage(createCmd("/news", "Apri il libro interattivo delle novita recenti", "/news", false));
                sender.sendMessage(createCmd("/news toggle", "Alterna tra apertura automatica libro e avviso cliccabile in chat all'accesso", "/news toggle", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/news reload", "Ricarica la configurazione del libro delle novita", "/news reload", false));
                }
                sender.sendMessage(MessageUtil.format("&7Suggerimento: puoi anche cliccare direttamente sul pulsante in fondo al libro!"));
            }
            case "bag", "zaino", "backpack", "borsa" -> {
                sender.sendMessage(MessageUtil.format("&6🎒 Dettagli Zaino Personale:"));
                sender.sendMessage(createCmd("/bag", "Apri il tuo zaino portatile ovunque ti trovi", "/bag", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/bag <player>", "Ispeziona e gestisci lo zaino di un altro giocatore", "/bag ", true));
                }
                sender.sendMessage(MessageUtil.format("&7Suggerimento: puoi espandere la capienza dello zaino (fino a 6 righe / 54 slot) tramite i gradi rank!"));
            }
            case "orario", "ora", "time" -> {
                sender.sendMessage(MessageUtil.format("&6⏰ Dettagli Comando Orario & Fuso Orario:"));
                sender.sendMessage(createCmd("/orario", "Visualizza la data e l'orario attuale (fuso orario italiano)", "/orario", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/orario reload", "Ricarica la configurazione del fuso orario", "/orario reload", false));
                }
                sender.sendMessage(MessageUtil.format("&7Suggerimento: l'orario del plugin e' sincronizzato con il fuso &eEurope/Rome&7."));
            }
            case "evento", "eventi", "event" -> {
                sender.sendMessage(MessageUtil.format("&6🏆 Dettagli Eventi a Premi:"));
                sender.sendMessage(createCmd("/evento", "Panoramica dell'evento corrente, stato e pulsanti", "/evento", false));
                sender.sendMessage(createCmd("/evento partecipa", "Conferma obbligatoria di partecipazione all'evento", "/evento partecipa", false));
                sender.sendMessage(createCmd("/evento abbandona", "Ritiro volontario dalla partecipazione all'evento", "/evento abbandona", false));
                sender.sendMessage(createCmd("/evento top", "Visualizza la classifica in tempo reale", "/evento top", false));
                sender.sendMessage(createCmd("/evento info", "Dettagli obbiettivi e premi della Top 3", "/evento info", false));
                sender.sendMessage(createCmd("/evento dungeon", "Teletrasporto sicuro a un dungeon (per il 3° posto)", "/evento dungeon", false));
                if (isAdmin) {
                    sender.sendMessage(createAdminCmd("/evento admin start [id]", "Avvia forzatamente l'evento a premi", "/evento admin start ", true));
                    sender.sendMessage(createAdminCmd("/evento admin stop", "Interrompe l'evento a premi in corso", "/evento admin stop", false));
                    sender.sendMessage(createAdminCmd("/evento admin kick <giocatore>", "Espelle un giocatore dalla partecipazione all'evento", "/evento admin kick ", true));
                    sender.sendMessage(createAdminCmd("/evento admin setprogress <player> <val>", "Imposta il punteggio di un giocatore", "/evento admin setprogress ", true));
                    sender.sendMessage(createAdminCmd("/evento admin reset", "Azzera partecipanti e progressi dell'evento", "/evento admin reset", false));
                    sender.sendMessage(createAdminCmd("/evento admin reload", "Ricarica il file events.yml", "/evento admin reload", false));
                    sender.sendMessage(createAdminCmd("/evento admin giveprize <giocatore> <premio>", "Assegna il premio a scelta a un vincitore", "/evento admin giveprize ", true));
                }
                sender.sendMessage(MessageUtil.format("&7Nota: e obbligatorio confermare la partecipazione per far conteggiare i blocchi!"));
            }
            default -> {
                sender.sendMessage(MessageUtil.format("&cSezione '&e" + section + "&c' non trovata."));
                sender.sendMessage(MessageUtil.format("&7Usa &e/aiuto&7 per vedere l'elenco completo delle sezioni disponibili."));
            }
        }

        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
    }

    private Component createCmd(String usage, String description, String clickCmd, boolean suggestOnly) {
        Component cmd = Component.text("  " + usage, NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Clicca per: " + description, NamedTextColor.YELLOW)))
                .clickEvent(suggestOnly ? ClickEvent.suggestCommand(clickCmd) : ClickEvent.runCommand(clickCmd));

        Component arrow = Component.text(" » ", NamedTextColor.DARK_GRAY);
        Component desc = Component.text(description, NamedTextColor.GRAY);

        return cmd.append(arrow).append(desc);
    }

    private Component createAdminCmd(String usage, String description, String clickCmd, boolean suggestOnly) {
        Component cmd = Component.text("  " + usage, NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("[ADMIN] " + description, NamedTextColor.LIGHT_PURPLE)))
                .clickEvent(suggestOnly ? ClickEvent.suggestCommand(clickCmd) : ClickEvent.runCommand(clickCmd));

        Component arrow = Component.text(" » ", NamedTextColor.DARK_GRAY);
        Component desc = Component.text(description, NamedTextColor.GRAY);

        return cmd.append(arrow).append(desc);
    }

    private Component createCategoryButton(String label, String section) {
        return Component.text("[" + label + "]")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Clicca per visualizzare la sezione: " + label, NamedTextColor.YELLOW)))
                .clickEvent(ClickEvent.runCommand("/aiuto " + section));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            boolean isAdmin = sender.isOp() || sender.hasPermission("caciottacore.admin");
            List<String> sections = new ArrayList<>(List.of("home", "warp", "tpa", "tpback", "sit", "sleep", "afk", "sb", "rank", "news", "bag", "orario", "evento", "eventi"));
            if (isAdmin) {
                sections.add("admin");
                sections.add("mod");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String sec : sections) {
                if (sec.startsWith(prefix)) {
                    matches.add(sec);
                }
            }
            return matches;
        }
        return List.of();
    }
}
