# 🧀 CaciottaCore - Plugin Minecraft (Paper 26.2)

**CaciottaCore** è un plugin completo per server Minecraft **PaperMC** (compatibile con **Paper 26.2** e Java 21+) che unisce:
1. **Sistema Home avanzato**: supporto a home multipli, conto alla rovescia personalizzabile (con annullamento al movimento o danno), suoni, particelle, tab-completion e chat interattiva (clicca per teletrasportarti).
2. **Sistema Autenticazione (Auth / Login & Register)**: login e registrazione obbligatori all'accesso, protezione crittografica sicura con **PBKDF2-HMAC-SHA256** e Salt crittografico, blocco totale delle azioni pre-login, effetti di cecità/lentezza, promemoria periodici e kick automatico per timeout o tentativi falliti.
3. **Sistema Rank completo**: gestione rank personalizzabili via `ranks.yml`, gerarchia con pesi (`weight`), permessi dinamici nativi Bukkit (`PermissionAttachment`), integrazione limiti home per rank, formattazione chat avanzata (`AsyncChatEvent` / Kyori Adventure), formattazione tablist e ordinamento visivo con Scoreboard Teams.
4. **Sistema Teletrasporto (TPA)**: richieste con pulsanti cliccabili in chat, scadenza e annullamento.
5. **Sistema Scoreboard laterale moderna**: scoreboard per giocatore flicker-free nativa Paper (Adventure API), animazione dinamica del titolo, numeri rossi nascosti (`NumberFormat.blank()`), statistiche PvP in tempo reale (uccisioni, morti, rapporto K/D), informazioni utente e server, integrazione dinamica con il rank (`{rank}` e `{rank_prefix}`), toggle individuale (`/sb`).
6. **Sistema Warp pubblico**: creazione e utilizzo di punti di teletrasporto pubblici per tutti i giocatori (inclusi utenti normali con `/setwarp`), teletrasporto con conto alla rovescia personalizzabile, cancellazione automatica su movimento o danno, elenco interattivo con pulsanti cliccabili (`/warps`), protezione da sovrascritture altrui ed eliminazione riservata al creatore o admin (`/delwarp`).
7. **Sistema Ritorno Morte (/tpback)**: teletrasporto al punto della propria ultima morte consentito solo entro 30 secondi e per un massimo di 3 volte al giorno (con contatore giornaliero persistente), notifica cliccabile alla morte e supporto a conto alla rovescia, suoni e particelle.
8. **Sistema Moderazione Completo**: gestione avanzata delle sanzioni staff con ban permanente (`/ban`), ban temporaneo (`/tempban`), revoca ban (`/unban`), espulsione immediata (`/kick`), mute permanente (`/mute`), mute temporaneo (`/tempmute`) e revoca mute (`/unmute`), con schermate personalizzate, blocco chat/messaggi privati e persistenza su `data/moderation.yml`.
9. **Sistema Novità del Server (/news)**: libro interattivo (Book GUI nativo Paper/Adventure) con gli aggiornamenti e le novità del server, mostrato automaticamente al login/registrazione qualora vi siano novità configurate, e consultabile in qualsiasi momento con `/news`.
10. **Sistema Sonno e Salto Notte (/sleep)**: comando per dormire con o senza letto (anche a terra sul posto), rispetto dell'orario vanilla (notte e temporali), annuncio globale in chat all'arrivo dell'ora con invito interattivo e pulsante cliccabile, passaggio automatico della notte e reset della statistica Phantom.
11. **Sistema AFK (/afk)**: passaggio in modalità Spettatore, blocco totale di qualsiasi tipo di azione (chat, comandi, interazioni, teletrasporti spectator) e ripristino istantaneo della gamemode e della posizione al minimo movimento con la tastiera.

---

## 📦 File JAR Compilato
Il file compilato pronto all'uso si trova in:
`target/CaciottaCore-1.0.0.jar`

Basta copiarlo nella cartella `plugins/` del tuo server PaperMC e avviare il server.

---

## 🚀 Comandi e Permessi

### 🏠 Comandi Home
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/sethome [nome]` | Imposta una home nella posizione attuale (default: `home`) | `caciottacore.home.set` | Tutti |
| `/home [nome]` | Teletrasportati a una delle tue home | `caciottacore.home.use` | Tutti |
| `/delhome <nome>` | Elimina una tua home esistente | `caciottacore.home.delete` | Tutti |
| `/homes` | Mostra l'elenco cliccabile delle tue home con coordinate | `caciottacore.home.list` | Tutti |

#### Limiti Home personalizzabili tramite permessi:
- `caciottacore.homes.<numero>` (es. `caciottacore.homes.5` permette fino a 5 home).
- `caciottacore.homes.unlimited` (home illimitate).
- `caciottacore.home.bypasscooldown` (teletrasporto istantaneo senza attesa).

---

### 🌀 Comandi Warp (Pubblici)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/setwarp <nome>` | Crea o aggiorna un punto di teletrasporto warp pubblico | `caciottacore.warp.set` | **Tutti (anche utenti normali)** |
| `/warp [nome]` | Teletrasportati al warp indicato (o mostra lista se vuoto) | `caciottacore.warp.use` | **Tutti** |
| `/delwarp <nome>` | Elimina un warp creato da te (o qualsiasi warp se admin) | `caciottacore.warp.delete` | **Tutti** |
| `/warps` | Mostra l'elenco cliccabile di tutti i warp disponibili | `caciottacore.warp.list` | **Tutti** |

- **Alias supportati**: `/setwarp` (`/creawarp`, `/addwarp`), `/warp` (`/tpwarp`), `/delwarp` (`/remwarp`, `/rwarp`, `/deletewarp`), `/warps` (`/warplist`, `/listwarps`).
- **Subcomandi su /warp**: puoi usare anche `/warp list`, `/warp set <nome>`, `/warp del <nome>`.
- **Protezione anticopia / sovrascrittura**: se un warp esiste già ed appartiene a un altro utente, solo il creatore originario o un admin può aggiornarlo o cancellarlo.
- **Limiti Warp personalizzabili**:
  - `caciottacore.warp.max.<numero>` (limite massimo di warp creabili per utente).
  - `caciottacore.warp.unlimited` (warp creabili illimitati).
  - `caciottacore.warp.bypasscooldown` (teletrasporto istantaneo senza conto alla rovescia, default: OP).
  - `caciottacore.warp.admin` (amministrazione ed eliminazione di qualsiasi warp sul server).

---

### 📊 Comandi Scoreboard
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/scoreboard` o `/sb` | Attiva o disattiva la tua scoreboard personale | `caciottacore.scoreboard.use` | Tutti |
| `/sb toggle` | Alterna la visualizzazione della scoreboard | `caciottacore.scoreboard.use` | Tutti |
| `/sb reload` | Ricarica la configurazione delle scoreboard in tempo reale | `caciottacore.scoreboard.admin` | Solo OP |

#### Placeholder supportati nella scoreboard:
- `{player}` / `{name}`: Nickname del giocatore
- `{rank}` / `{rank_prefix}`: Grado o prefisso del giocatore dal sistema Rank
- `{x}` / `{y}` / `{z}`: Coordinate della posizione attuale del giocatore
- `{coords}` / `{coordinates}`: Coordinate formattate complete (`X, Y, Z`)
- `{direction}` / `{direction_short}`: Direzione cardinale (`Nord`, `Sud`, `Est`, `Ovest` o `N`, `S`, `E`, `W`)
- `{online}` / `{max_online}`: Giocatori online e slot massimi
- `{ping}`: Latenza di rete in ms
- `{kills}`: Uccisioni PvP totali del giocatore
- `{deaths}`: Morti totali del giocatore
- `{kdr}`: Rapporto Kill/Death (es. `1.50`)
- `{mob_kills}`: Mostri e mob uccisi
- `{world}`: Nome del mondo in cui si trova il giocatore
- `{level}`: Livello di esperienza
- `{health}` / `{max_health}`: Vita attuale e massima
- `{homes}` / `{max_homes}`: Numero di home salvate e massimo consentito
- `{warps}`: Numero totale di warp disponibili sul server
- `{status}`: Stato login (`Autenticato` / `Non Autenticato`)
- `{date}` / `{time}`: Data (GG/MM/AAAA) e ora corrente (HH:mm)

---

### 👑 Comandi Rank
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/rank list` | Mostra l'elenco di tutti i rank, pesi, limiti home e player online | `caciottacore.rank.use` | Tutti |
| `/rank info [giocatore]` | Mostra dettagli completi del proprio rank o di un altro giocatore | `caciottacore.rank.use` | Tutti |
| `/rank set <giocatore> <rank>` | Assegna un rank a un giocatore (online o offline) | `caciottacore.rank.admin` | Solo OP |
| `/rank reset <giocatore>` | Reimposta il rank di un giocatore al rank predefinito | `caciottacore.rank.admin` | Solo OP |
| `/rank default <rank>` | Imposta quale rank è il predefinito per i nuovi utenti | `caciottacore.rank.admin` | Solo OP |
| `/rank reload` | Ricarica `ranks.yml` e aggiorna permessi/tablist a tutti gli online | `caciottacore.rank.admin` | Solo OP |

#### Rank preconfigurati in `ranks.yml`:
- **Owner** (peso 100): `&4[Owner]`, 100 home, permessi completi `*`.
- **Admin** (peso 80): `&c[Admin]`, 50 home, permessi admin e rank admin.
- **Mod** (peso 60): `&6[Mod]`, 20 home, gestione home, tpa, scoreboard e colore chat.
- **Helper** (peso 40): `&e[Helper]`, 10 home, home, tpa, scoreboard e colore chat.
- **VIP+** (peso 25): `&b[VIP+]`, 8 home, bypass cooldown teletrasporto e colore chat.
- **VIP** (peso 20): `&a[VIP]`, 5 home, colore chat.
- **Utente** (peso 1, default): `&7[Utente]`, 3 home, comandi base.

---

### 🔐 Comandi Autenticazione
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/register <password> <conferma>` | Registra un nuovo account con password | *Nessuno (aperto a tutti)* | Tutti |
| `/login <password>` | Accedi al server inserendo la tua password | *Nessuno (aperto a tutti)* | Tutti |
| `/changepassword <vecchia> <nuova>` | Modifica la password del tuo account | `caciottacore.auth.changepassword` | Tutti |
| `/authadmin reload` | Ricarica la configurazione del plugin (`config.yml`) | `caciottacore.admin` | Solo OP |
| `/authadmin unregister <player>` | Rimuove la registrazione di un utente | `caciottacore.admin` | Solo OP |

---

### 🌐 Comandi Teletrasporto (TPA)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/tpa <giocatore>` | Invia una richiesta di teletrasporto a un giocatore | `caciottacore.tpa.use` | Tutti |
| `/tpaccept [giocatore]` | Accetta la richiesta ricevuta (o clicca sul bottone in chat) | `caciottacore.tpa.use` | Tutti |
| `/tpdeny [giocatore]` | Rifiuta la richiesta ricevuta (o clicca sul bottone in chat) | `caciottacore.tpa.use` | Tutti |
| `/tpacancel` | Annulla la richiesta inviata | `caciottacore.tpa.use` | Tutti |

- Permesso bypass attesa: `caciottacore.tpa.bypasscooldown` (OP).
- Scadenza richiesta configurabile (default: 60 secondi).
- Conto alla rovescia di teletrasporto configurabile (default: 3 secondi, annullabile se ci si muove o subisce danno).

---

### 💀 Comando Ritorno Morte (TPBack)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/tpback` (o `/back`) | Teletrasportati nel punto della tua ultima morte | `caciottacore.tpback.use` | Tutti |

- **Finestra temporale**: consentito **esclusivamente entro 30 secondi** dal momento del decesso.
- **Limite giornaliero**: eseguibile per un **massimo di 3 volte durante la giornata**. I dati sono memorizzati persistentemente in `data/tpback.yml` per evitare reset arbitrari.
- **Notifica alla morte**: all'evento di morte viene inviato un messaggio in chat cliccabile che indica il tempo rimanente e gli utilizzi giornalieri disponibili.
- **Permessi aggiuntivi**:
  - `caciottacore.tpback.bypasscooldown`: teletrasporto istantaneo senza attendere il conto alla rovescia (default: OP).
  - `caciottacore.tpback.unlimited`: rimozione totale del vincolo dei 3 usi giornalieri (default: OP).
  - `caciottacore.tpback.uses.<numero>`: imposta un numero massimo personalizzato di utilizzi giornalieri per un gruppo/giocatore.

---

### 🛡️ Comandi Moderazione (Staff)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/ban <giocatore> [motivo]` | Banna permanentemente un giocatore dal server | `caciottacore.mod.ban` | Solo OP |
| `/tempban <giocatore> <durata> [motivo]` | Banna temporaneamente un giocatore (es: `10m`, `2h`, `1d`, `7d`) | `caciottacore.mod.ban` | Solo OP |
| `/unban <giocatore>` (o `/pardon`) | Revoca il ban attivo di un giocatore | `caciottacore.mod.unban` | Solo OP |
| `/kick <giocatore> [motivo]` (o `/caccia`) | Espelle/caccia immediatamente un giocatore online dal server | `caciottacore.mod.kick` | Solo OP |
| `/mute <giocatore> [motivo]` | Muta permanentemente un giocatore impedendogli di scrivere in chat | `caciottacore.mod.mute` | Solo OP |
| `/tempmute <giocatore> <durata> [motivo]` | Muta temporaneamente un giocatore per la durata specificata | `caciottacore.mod.mute` | Solo OP |
| `/unmute <giocatore>` | Revoca il mute di un giocatore ripristinando la scrittura in chat | `caciottacore.mod.unmute` | Solo OP |

- **Unità temporali supportate per durate**:
  - `s` (secondi, es. `30s`)
  - `m` (minuti, es. `15m`)
  - `h` (ore, es. `2h` o `12h`)
  - `d` (giorni, es. `1d`, `7d`, `30d`)
  - `w` (settimane, es. `1w`, `2w`)
- **Protezione Staff & Bypass**: i comandi di moderazione non possono essere utilizzati contro se stessi o contro membri dello staff protetti dal permesso `caciottacore.mod.bypass`.
- **Blocco Chat e Messaggi Privati**: un giocatore mutato non può inviare messaggi globali né usare comandi di messaggistica privata (`/msg`, `/tell`, `/w`, `/r`).
- **Persistenza**: tutti i ban e i mute attivi sono salvati su disco in `data/moderation.yml` e rimangono attivi anche dopo riavvii o reload del server.

---

---

### 🪑 Sistema Seduta (Sit)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/sit` (o `/siediti`, `/chair`) | Siediti sul posto o alzati se sei gia seduto | `caciottacore.sit` | Tutti |

- **Sedersi sulle sedie**: cliccando con il tasto destro su scale (`Stairs`) o gradini (`Slabs`) con la mano vuota, il giocatore si siede automaticamente rivolto nella direzione dello schienale della sedia.
- **Alzarsi**: premendo il tasto sneak (`Shift`) o digitando nuovamente `/sit`, il giocatore si alza in sicurezza senza incastrarsi nei blocchi.
- Pulizia automatica: le sedie temporanee non vengono mai salvate nei file del mondo (`setPersistent(false)`) e vengono rimosse all'uscita dal server, alla morte o al teletrasporto.

---

### 🌙 Sistema Sonno (Sleep)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/sleep` (o `/dormi`) | Mettiti a dormire sul posto o nel letto per passare la notte | `caciottacore.sleep.use` | **Tutti** |
| `/sleep status` | Visualizza l'ora attuale del mondo e i giocatori che stanno dormendo | `caciottacore.sleep.use` | **Tutti** |
| `/sleep reload` | Ricarica le configurazioni e il ciclo del sonno | `caciottacore.sleep.admin` | Solo OP |

- **Dormire anche senza letto**: se non c'è un letto nelle vicinanze, il comando posiziona il giocatore a riposare direttamente sul terreno in tutta sicurezza con animazione e feedback Actionbar.
- **Rispetto dell'orario vanilla**: il sonno è consentito esclusivamente nelle ore notturne (tick 12542-23460, ovvero dalle 18:30 circa) o in caso di temporale (`world.isThundering()`).
- **Annuncio Globale Interattivo**: non appena scatta l'ora per andare a dormire (o inizia un temporale), viene inviato un annuncio in chat globale a tutti i giocatori con un **pulsante interattivo cliccabile** `[CLICCA QUI PER DORMIRE]` per eseguire immediatamente `/sleep`.
- **Passaggio della Notte**: configurabile (predefinito a 1 giocatore con conto alla rovescia di 4 secondi) per far avanzare l'ora del mondo al nuovo mattino e ripulire il maltempo.
- **Protezione Phantom**: all'avvio del sonno o al salto della notte viene azzerata la statistica `TIME_SINCE_REST`, impedendo lo spawn dei Phantom.
- **Svegliarsi**: è sufficiente premere il tasto sneak (`Shift`) o digitare di nuovo `/sleep` per alzarsi in qualsiasi momento.

---

### 💤 Sistema AFK
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/afk` (o `/away`) | Entra o esci dalla modalità AFK (Spettatore) | `caciottacore.afk.use` | **Tutti** |
| `/afk list` | Visualizza tutti i giocatori attualmente in AFK e da quanto tempo | `caciottacore.afk.use` | **Tutti** |
| `/afk <giocatore>` | Imposta o revoca lo stato AFK di un altro giocatore | `caciottacore.afk.admin` | Solo OP |
| `/afk reload` | Ricarica la configurazione del sistema AFK | `caciottacore.afk.admin` | Solo OP |

- **Modalità Spettatore protetta**: all'attivazione il giocatore entra in modalità `SPECTATOR`, preservando la gamemode e la posizione esatta originaria.
- **Titolo fisso a schermo intero**: durante l'AFK viene proiettato permanentemente a schermo il titolo `SEI AFK` con sottotitolo `Una piccola pausa ci vuole` (rimosso istantaneamente all'uscita).
- **Suffisso nella Tablist**: nella lista tab dei giocatori connessi viene aggiunto un elegante suffisso `[AFK]` accanto al nome del giocatore, che si rimuove automaticamente non appena torna attivo.
- **Blocco totale delle azioni**: mentre si è in AFK è impedito qualsiasi tipo di azione: scrittura in chat, esecuzione comandi (eccetto `/afk`), interazione con blocchi o entità, drop o raccolta oggetti e teletrasporti verso giocatori.
- **Uscita istantanea al movimento tastiera**: al minimo movimento con la tastiera (input WASD, salto o sneak con spostamento coordinate), il movimento viene annullato e il giocatore viene immediatamente rimosso dall'AFK, ripristinando la sua gamemode precedente e riportandolo alla posizione originaria.
- **Disconnessione sicura**: se il giocatore si disconnette mentre è in AFK, il suo stato viene ripristinato prima del salvataggio del profilo, evitando che rientri in modalità spettatore.

---

### 📢 Comandi Broadcast (Annunci Globali)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/broadcast <messaggio>` | Invia un annuncio in chat con banner decorativo a tutti i giocatori | `caciottacore.broadcast` | Solo OP |
| `/broadcast title <titolo> [\| sottotitolo]` | Mostra un annuncio a schermo intero con Title e Sottotitolo | `caciottacore.broadcast` | Solo OP |
| `/broadcast actionbar <messaggio>` | Mostra un annuncio rapido nella barra sopra l'inventario | `caciottacore.broadcast` | Solo OP |

- **Alias supportati**: `/bc`, `/annuncio`, `/alert`, `/yell`.
- **Notifica sonora**: riproduce automaticamente un suono di notifica a tutti i giocatori online quando viene inviato un annuncio.
- **Stile banner**: configurabile da `config.yml` con supporto codici colore `&` ed esadecimali.

---

### 📰 Sistema Novità del Server (News)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/news` (o `/novita`, `/updates`) | Visualizza il libro interattivo con le novità recenti del server | `caciottacore.news.use` | Tutti |
| `/news toggle` | Alterna tra apertura automatica del libro o avviso cliccabile in chat all'accesso | `caciottacore.news.use` | Tutti |
| `/news reload` | Ricarica il contenuto del libro delle novità da `config.yml` | `caciottacore.news.admin` | Solo OP |

- **Apertura automatica al login / registrazione**: non appena un utente effettua il login, la registrazione o il rientro da sessione IP attiva, il server apre automaticamente a schermo intero il libro con le novità recenti (qualora ve ne siano di configurate in `config.yml`).
- **Modalità avviso chat (Silenziosa)**: i giocatori possono disattivare l'apertura automatica del libro cliccando direttamente sul pulsante interattivo `[Non mostrare all'accesso]` presente nell'ultima pagina del libro oppure digitando `/news toggle`. In questo modo, all'accesso riceveranno invece un elegante messaggio in chat con `[CLICCA QUI]` per aprirlo a piacimento.
- **Nessun ingombro nell'inventario**: il libro viene proiettato direttamente come interfaccia grafica client nativa (Kyori Adventure API) senza occupare slot o droppare oggetti.
- **Persistenza e Reload a caldo**: le preferenze dei giocatori vengono salvate su `data/news.yml` e mantenute tra i riavvii; la configurazione del libro e dei messaggi si ricarica istantaneamente con `/news reload` e `/reload`.

---

### 🎒 Sistema Zaino Personale (Bag)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/bag` (o `/backpack`, `/zaino`, `/borsa`) | Apre il proprio zaino portatile personale | `caciottacore.bag.use` | **Tutti** |
| `/bag <giocatore>` | Ispeziona e modifica lo zaino di un altro giocatore (online o offline) | `caciottacore.bag.admin` | Solo OP |

- **Capienza personalizzabile e scalabile**: dimensione predefinita di 3 righe (27 slot), espandibile fino a 6 righe (54 slot) tramite i permessi `caciottacore.bag.rows.<1-6>` o `caciottacore.bag.rows.max` (perfetto per i Rank VIP).
- **Protezione anti-duplicazione**: inventario condiviso in tempo reale e sincronizzato atomicamente, nessun rischio di perdita oggetti o desync tra giocatori e staff.
- **Ispezione staff completa**: i moderatori e admin possono visualizzare e gestire gli zaini dei giocatori sia online sia offline.
- **Protezione Shulker Box**: opzione configurabile per impedire l'inserimento di Shulker Box nello zaino per evitare storage infinito o bug NBT.
- **Persistenza e sicurezza**: salvataggio automatico continuo in `data/bags/<uuid>.yml` alla chiusura dell'inventario, disconnessione o riavvio del server.

---

### 📖 Comando Guida e Aiuto
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/aiuto [sezione]` | Mostra una guida interattiva con riassunto di tutti i comandi disponibili | `caciottacore.help` | **Tutti** |

- **Alias supportati**: `/help`, `/comandi`, `/guida`, `/guide`.
- **Interattività completa**: ogni comando è cliccabile (lo esegue direttamente o lo inserisce pronto all'uso nella barra chat) e passando il mouse su di esso viene mostrata la descrizione dettagliata con suggerimenti.
- **Navigazione a sezioni**: pulsanti rapidi in cima e supporto a sottocomandi specifici: `/aiuto home`, `/aiuto warp`, `/aiuto tpa`, `/aiuto sit`, `/aiuto sb`, `/aiuto rank`, `/aiuto admin`.
- **Filtro permessi intelligente**: i comandi di amministrazione dello staff vengono mostrati solo agli utenti con privilegi (`caciottacore.admin` o OP).

---

### 🔄 Comando Ricarica Globale (Reload)
| Comando | Descrizione | Permesso | Default |
|---|---|---|---|
| `/reload` | Ricarica tutte le configurazioni, i file e i moduli di CaciottaCore in tempo reale | `caciottacore.admin` / `caciottacore.reload` | Solo OP |
| `/core reload` o `/caciottacore reload` | Comando alternativo di reload sicuro | `caciottacore.admin` / `caciottacore.reload` | Solo OP |

- **Alias supportati**: `/corereload`, `/caciottareload`, `/ccreload`, `/caciottacore reload`, `/core reload`.
- **Cosa ricarica istantaneamente**:
  - `config.yml`: Prefisso, opzioni di teletrasporto, broadcast, formati e tutti i testi dei messaggi (con sincronizzazione e auto-aggiornamento su disco).
  - `ranks.yml`: Ricarica di tutti i rank, aggiornamento permessi dinamici, colori chat e tablist per tutti i giocatori connessi.
  - Scoreboard: Riavvio ticker animazioni e aggiornamenti, ricostruzione della sidebar con le nuove impostazioni per tutti i giocatori online.
  - `data/warps.yml`: Rilettura e sincronizzazione di tutti i warp registrati.
  - Cache Home: Rilascio e sincronizzazione immediata dei file `.yml` delle home.
  - Sistema Auth: Ricarica mappature account da disco.
  - Sistema Sit: Pulizia sedie temporanee se disabilitato.
  - Sistema TPBack: Ricarica configurazione e archivio utilizzi giornalieri (`data/tpback.yml`).
  - Sistema Moderazione: Ricarica archivio sanzioni, ban e mute attivi (`data/moderation.yml`).
  - Sistema News: Ricarica pagine e configurazione del libro delle novità.
  - Sistema Bag: Salvataggio e ricarica cache degli inventari degli zaini.
  - Sistema Sleep: Ricarica task del ciclo orario notturno, annunci e impostazioni di salto notte.
  - Sistema AFK: Ripristino gamemode e ricarica impostazioni modulo AFK.

---

## 🔒 Sicurezza e Protezione Pre-Login
Quando un giocatore entra nel server senza essere autenticato:
- Viene bloccato il movimento (può solo ruotare la testa).
- Viene bloccata la chat e tutti i comandi (eccetto `/login` e `/register`).
- Vengono bloccati rottura blocchi, piazzamento blocchi e interazione con casse, porte, bottoni e leve.
- Il giocatore è immune ai danni (godmode temporaneo per non morire dai mob).
- Non può colpire altre entità, né raccogliere o droppare oggetti o aprire l'inventario.
- Vengono applicati effetti di cecità e lentezza configurabili.
- Un timer espelle il giocatore se non si autentica entro il tempo limite (configurabile).
- Le password sono protette con **PBKDF2WithHmacSHA256 (65.536 iterazioni, salt a 16 byte e confronto constant-time)**.

---

## 🛠️ Come Ricompilare
Se desideri modificare il codice sorgente o ricompilare il plugin:

```powershell
# Su Windows tramite Maven Wrapper incluso:
.\mvnw.cmd clean package

# Su Linux/macOS:
./mvnw clean package
```
Il JAR generato verrà ricreato nella cartella `target/`.

---

## 📚 Guida per gli Sviluppatori
Per le direttive architetturali sulla creazione di nuovi comandi (messaggi in italiano, documentazione in `/aiuto` con filtri permessi e supporto al reload a caldo), consulta la guida dedicata:
👉 **[guida.md](file:///c:/Users/gian/Documents/CaciottaCore/guida.md)**

