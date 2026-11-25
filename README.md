# Policy Decoder

**Uno strumento di analisi che smonta la propaganda anti-trans nei testi, nei media e nelle dichiarazioni pubbliche, mettendo a disposizione le prove per segnalarla.**

Sito: <https://policy-decoder.com> (accesso su richiesta, vedi [Accesso](#accesso)) ·
Sviluppato per [ACET](https://associazionetransgenere.org) da Elisa Ruscio.

## Il problema

I testi che attaccano i diritti delle persone transgender e non binarie sono
spesso costruiti con cura: citazioni selezionate, dati fuori contesto, studi
presentati come più solidi di quanto siano, una retorica emotiva che fa
sembrare un'opinione un fatto. Smontarli a mano richiede ore di lavoro di una
persona esperta. Policy Decoder automatizza la prima analisi e la rende
ripetibile, con le fonti a portata di mano.

## Cosa fa

Si incolla un testo, un link o un PDF e si ottiene un'analisi strutturata in
sezioni:

| Sezione | Contenuto |
| --- | --- |
| **Sommario** | cosa dichiara il testo e qual è l'intento reale |
| **Livello di pericolo** | punteggio da 0 a 100 con motivazione |
| **Tecniche retoriche** | le tecniche di persuasione usate, con le citazioni |
| **Fact-check** | verifica delle affermazioni, con fonti verificabili |
| **Contesto legale** | le norme e i principi giuridici coinvolti |
| **Contro-narrazione** | la risposta fattuale, pronta da usare |
| **Conformità normativa italiana** | per articoli e dichiarazioni in italiano: i punti precisi che violano il codice deontologico dei giornalisti (ODG), il codice dell'autodisciplina pubblicitaria (IAP), il Codice del Consumo, con l'articolo citato, per preparare segnalazioni formali |
| **Slide** | sintesi pensata per essere condivisa |

L'analisi si esporta in PDF. L'interfaccia è in italiano e in inglese.

### Come è stata costruita l'affidabilità

Uno strumento che cita leggi e studi deve evitare di inventarli. Le scelte
principali:

- **Elenchi chiusi di riferimenti.** Gli articoli normativi (ODG, IAP, Codice
  del Consumo) sono verificati sui testi ufficiali e inseriti nel prompt come
  elenco chiuso: al modello è vietato citare qualcosa che non c'è.
- **Nessuna sezione forzata.** Se un testo non è italiano o non è un testo
  giornalistico, la sezione sulla conformità normativa viene omessa. Se un ente
  non ha competenza (per esempio AGCOM sulla stampa), l'analisi lo dice invece
  di inventare una violazione.
- **Archivio fonti (RAG).** Documenti di riferimento caricati da un
  amministratore, indicizzati su Postgres con pgvector e iniettati nel
  contesto dell'analisi, così le fonti non dipendono da una ricerca live sul web.
- **Risposte troncate recuperate.** Se il modello esaurisce lo spazio di
  output a metà JSON, il server ripara la risposta invece di scartarla.
- **Protezioni sul recupero degli articoli.** Il download da URL blocca indirizzi
  interni e privati (anti-SSRF), anche dopo risoluzione DNS e redirect.

## Stack

- **Backend:** Scala 3, ZIO, zio-http, Tapir, Quill, circe
- **Frontend:** Laminar (Scala.js), un'unica SPA servita dallo stesso processo
- **Dati:** PostgreSQL con pgvector e migrazioni Flyway
- **IA:** motore configurabile: Anthropic, OpenRouter o Ollama self-hosted
- **Infrastruttura:** Docker, Coolify su VPS Hetzner, Cloudflare davanti
- **Sicurezza:** password con bcrypt, 2FA TOTP opzionale, JWT, rate limiting
  per IP, quota mensile per utente

Il contratto tra frontend e backend (path, metodi, forma del JSON) è definito
una volta sola in un modulo condiviso JVM+JS, e il client del browser è
generato da quelle stesse definizioni.

## Accesso

Lo strumento è ad accesso riservato. Dalla pagina di login si può inviare una
richiesta di accesso, che viene approvata manualmente; l'uso prevede un
abbonamento o una donazione ad ACET.

## Documentazione tecnica

Il resto di questo file è la documentazione per chi sviluppa o gestisce
l'istanza.

- [Architettura](#architettura)
- [File per file](#file-per-file)
- [Sviluppo locale](#sviluppo-locale)
- [CLI di amministrazione](#cli-di-amministrazione)
- [Archivio fonti (RAG)](#archivio-fonti-rag)
- [Deploy (Hetzner + Coolify)](#deploy-hetzner--coolify)
- [Sostenibilità dei costi](#sostenibilità-dei-costi)
- [Trade-off consapevoli](#trade-off-consapevoli)

## Architettura

Un solo processo JVM (`decoder.backend.Main`) che serve sia le API JSON
(`/api/...`, via Tapir su zio-http) sia i file statici del frontend
compilato (ScalaJS + Laminar) — niente CORS, niente secondo servizio
Nginx/Vite, niente edge functions. Postgres come unico datastore.

```
modules/
  shared/    DTO + definizioni Tapir condivise (JVM+JS, crossProject)
  backend/   server ZIO/Tapir/Quill, motore di analisi, CLI di amministrazione
  frontend/  SPA Laminar (ScalaJS), compilata in JS e servita dal backend
```

Ispirato a <https://rockthejvm.com/articles/zio-full-stack-webapp> (stesso
stack: ZIO, Tapir, Laminar, Quill).

Autenticazione: email + password (bcrypt) + 2FA opzionale (TOTP, RFC 6238),
sessione **JWT stateless** in `sessionStorage` del browser, rimandato come
header `Authorization: Bearer <token>` — nessun cookie, nessun endpoint di
logout lato server (vedi [Trade-off consapevoli](#trade-off-consapevoli)).
Gli account si creano/rinnovano da CLI (`InvitaUtente`/`Abilita2fa`); chi non
ne ha uno può inviare una richiesta di accesso dal form "Registrati", che
finisce in una coda approvata a mano (`GestisciRichieste`): nessun account è
attivo senza approvazione.

## File per file

### `modules/shared` — contratto condiviso tra backend e frontend

- **`Dto.scala`** — tutti i DTO scambiati via JSON (circe), incluso
  `RisultatoAnalisi` con le sezioni prodotte dal motore di analisi
  (summary, danger_score, rhetoric, factcheck, legal, counter_narrative,
  slides, normativa_italiana). Tutte opzionali: una sezione assente nel JSON
  del modello è semplicemente `None`.
- **`Endpoints.scala`** — le definizioni Tapir (`login`, `richiediAccesso`,
  `usage`, `analyze`, `fetchUrl`, `exportPdf`, `uploadPdf`): un solo posto
  dove path, metodi HTTP e shape del JSON sono
  tenuti sincronizzati tra backend (che le implementa) e frontend (che ne
  genera un client sttp). L'IP del chiamante è estratto dall'header
  `X-Forwarded-For` via `extractFromRequest` — trasparente sul wire, ma il
  client generato richiede comunque un valore positionale per quell'input
  (vedi `Api.scala`).

### `modules/backend` — server

- **`Main.scala`** — entry point: esegue le migrazioni Flyway, poi avvia
  zio-http servendo sia le rotte API (Tapir) sia i file statici del
  frontend compilato (`resources/static/`, copiati lì durante la build
  Docker). Le rotte statiche sono un catch-all `GET /*`, più specifico del
  catch-all di Tapir: per questo gli endpoint dell'API devono essere POST
  (un GET sotto `/api` verrebbe intercettato dalla rotta statica e
  risponderebbe 500). È il punto da guardare se un nuovo endpoint "non
  risponde".
- **`config/AppConfig.scala`** — legge `application.conf` (Typesafe Config),
  espone porta, JWT secret, connection string Postgres, configurazione del
  motore IA (provider + credenziali per Anthropic/OpenRouter/Ollama), quota
  mensile di default.
- **`db/Modelli.scala`** — le 3 case class che rispecchiano le tabelle
  Postgres: `WhitelistEntry` (account + credenziali + TOTP), `EventoSicurezza`
  (log rate-limiting), `AnalisiRichiesta` (log di ogni analisi, per la
  quota mensile).
- **`db/Db.scala`** — converte `DATABASE_URL` (formato `postgres://...`,
  stesso di Prisma) in un `DataSource` JDBC, esegue le migrazioni Flyway
  all'avvio, espone il layer Quill (`Quill.Postgres` con naming strategy
  snake_case).
- **`db/Repo.scala`** — tutte le query Quill in un solo posto: whitelist
  (lookup, upsert, abilitazione 2FA), log eventi di sicurezza (con conteggio
  per rate-limiting), log delle analisi (con conteggio per la quota
  mensile). `upsertWhitelist` non tocca mai `totpSecret` in conflitto:
  rinnovare un account non deve disabilitare un 2FA già configurato.
- **`db/migration/V1__init.sql`** — schema Flyway, porta 1:1 i tre modelli
  Prisma della versione precedente in tabelle snake_case.
- **`sicurezza/Auth.scala`** — login: rate-limit per IP (5 tentativi/10min),
  verifica whitelist + scadenza abbonamento, bcrypt (cost 12, in thread pool
  dedicato per non bloccare l'event loop ZIO), TOTP opzionale (tolleranza
  ±1 finestra di 30s), firma/verifica JWT (HS256, validità 30 giorni).
- **`sicurezza/Sicurezza.scala`** — rilevazione brute-force/DoS generica:
  conta gli eventi recenti per IP+endpoint in una finestra temporale.
- **`sicurezza/Base32.scala`** — codifica/decodifica Base32 (RFC 4648) per i
  segreti TOTP, stesso formato prodotto da `otplib` nella versione
  precedente (compatibilità con segreti già distribuiti).
- **`http/HttpClient.scala`** — un solo backend sttp (async-http-client su
  ZIO) condiviso da motore IA e recupero articoli, per evitare backend
  duplicati.
- **`ia/PromptSistema.scala`** — il prompt di sistema (rigore scientifico,
  framework di analisi propagandistica, riferimenti legali, formato JSON di
  output): resta solo lato server, mai esposto al client.
- **`ia/MotoreIA.scala`** — chiamate ai 3 provider supportati (Anthropic,
  OpenRouter, Ollama), costruzione manuale del JSON di richiesta (le shape
  differiscono per provider), estrazione del JSON strutturato dalla
  risposta con riparazione di JSON troncato (può succedere se la risposta
  supera `max_tokens`): rimuove l'ultimo valore incompleto e richiude le
  parentesi rimaste aperte.
- **`quota/Quota.scala`** — quota mensile per utente; `limiteMensile = -1`
  in whitelist significa "illimitato" (account staff/admin).
- **`fonti/Embeddings.scala`** — chiama l'endpoint OpenAI-compatible
  `/v1/embeddings` esposto da Ollama (o da un gateway self-hosted davanti a
  Ollama, es. OmniRoute) per trasformare testo in vettore. Usa sempre
  `OllamaCfg` (`OLLAMA_BASE_URL`), indipendentemente da `AI_PROVIDER`: le
  fonti private restano sempre sull'infrastruttura self-hosted anche se
  l'analisi vera e propria usa un provider esterno.
- **`fonti/FontiRepo.scala`** — accesso JDBC diretto (non Quill, che non
  supporta il tipo colonna `vector` né l'operatore di distanza `<->`) alle
  tabelle `fonte_documento`/`fonte_chunk`: inserimento documenti/chunk,
  ricerca per similarità (cosine distance, pgvector).
- **`fonti/Fonti.scala`** — `contestoPer(testo)`: recupero RAG chiamato da
  `EndpointsImpl` prima di ogni analisi. Funzionalità opt-in — se gli
  embeddings falliscono o non ci sono chunk pertinenti, ritorna stringa
  vuota e l'analisi prosegue normalmente senza RAG.
- **`cli/IngestaFonte.scala`** — importa un documento (PDF o testo semplice)
  nell'archivio fonti: estrazione testo (PDFBox per i PDF), chunking a
  dimensione fissa (1500 caratteri, overlap 200), embedding e indicizzazione
  di ogni chunk. Uso: vedi [Archivio fonti (RAG)](#archivio-fonti-rag).
- **`db/migration/V2__fonti_pgvector.sql`** — abilita l'estensione
  `vector` e crea le tabelle `fonte_documento`/`fonte_chunk` con indice HNSW
  (cosine distance). `vector(768)` è dimensionato per `nomic-embed-text`
  (default `EMBEDDING_MODEL`): un modello con dimensione diversa richiede di
  adattare questa colonna PRIMA del primo avvio (Flyway non permette di
  rieseguire una migration già applicata).
- **`articolo/EstraiArticolo.scala`** — recupero e pulizia (Readability4J)
  di un articolo da URL. Protezioni anti-SSRF: solo `http`/`https`, blocco di
  `localhost` e di IP privati/loopback/link-local (verificati anche dopo
  risoluzione DNS e ad ogni redirect seguito manualmente, max 3), cap sulla
  dimensione del corpo (3MB, verificato dopo il download completo — non è
  streaming reale: semplificazione consapevole, non essendo stata aggiunta
  una dipendenza fs2/zio-streams).
- **`api/EndpointsImpl.scala`** — collega le definizioni Tapir condivise
  alla logica di business: rate-limit, validazione input, controllo quota,
  chiamata al motore IA, log delle analisi. Un solo tipo `Env` per tutti gli
  endpoint (anche quelli che non usano tutte le capacità), per evitare
  complicazioni di varianza nel combinarli in un'unica lista.
- **`cli/InvitaUtente.scala`** — crea o rinnova un account (whitelist +
  password), porta di `scripts/invita-utente.ts`. Uso: vedi
  [CLI di amministrazione](#cli-di-amministrazione).
- **`cli/Abilita2fa.scala`** — abilita il 2FA su un account esistente,
  stampa un QR ASCII (resa modulo-per-modulo con l'API di basso livello di
  ZXing, per evitare artefatti di scaling nel terminale) e il codice per
  l'inserimento manuale. Porta di `scripts/abilita-2fa.ts`.
- **`resources/application.conf`** — mapping tra variabili d'ambiente e
  configurazione (vedi `.env.example`).
- **`resources/static/`** — `index.html` (shell SPA) e `style.css` (stile
  minimo); il JS compilato dal frontend ScalaJS viene copiato qui durante
  la build Docker (non è versionato, vedi `.gitignore`).

### `modules/frontend` — SPA Laminar

- **`Main.scala`** — bootstrap: monta `App.root` nel `<div id="app">` al
  `DOMContentLoaded`.
- **`App.scala`** — componente radice: mostra `LoginView` o `AnalyzeView` a
  seconda della presenza di un JWT valido (in `sessionStorage`); gestisce
  logout e aggiornamento della quota (torna al login se il server risponde
  401, cioè token scaduto/non valido).
- **`Api.scala`** — client sttp generato dalle definizioni Tapir condivise,
  helper per leggere/scrivere token ed email in `sessionStorage`.
- **`LoginView.scala`** — form email/password/codice 2FA opzionale.
- **`AnalyzeView.scala`** — vista principale: tab testo/URL, selettore
  lingua di output, invio analisi, rendering delle sezioni del risultato.
  Nessuna pretesa di replicare pixel-per-pixel la UI React precedente
  (645 righe): stessa logica, presentazione essenziale.

### Root

- **`build.sbt`** — cross-build a 3 moduli (`shared` JVM+JS, `backend`,
  `frontend`), versioni delle dipendenze centralizzate, configurazione
  `sbt-assembly` (fat jar per il Dockerfile).
- **`Dockerfile`** — build multi-stage: compila frontend (ScalaJS) e backend
  (fat jar) in un'immagine con sbt, copia solo il risultato in un'immagine
  runtime con la sola JRE.
- **`docker-compose.yml`** — uso locale o deploy Coolify di tipo "Docker
  Compose" (app + Postgres); non richiesto se si usa un Postgres gestito
  separatamente da Coolify.
- **`.env.example`** — tutte le variabili d'ambiente lette da
  `application.conf`.
- **`LICENSE`** — MIT.

## Sviluppo locale

Serve una JVM 21, sbt e Node non è richiesto (il frontend è ScalaJS, non
richiede npm). Un Postgres locale (o via `docker compose up db`).

```bash
cp .env.example .env   # e compilare i valori
export $(cat .env | grep -v '^#' | xargs)   # o usare direnv/dotenv-cli
sbt backend/run
```

Per lavorare sul frontend con reload rapido durante lo sviluppo:
`sbt ~frontend/fastLinkJS`, poi copiare l'output in
`modules/backend/src/main/resources/static/` (lo stesso passo che fa il
Dockerfile in produzione).

## CLI di amministrazione

Gli account si creano/rinnovano da riga di comando; le richieste di accesso
dal form pubblico si approvano con `GestisciRichieste`.

```bash
# Crea/rinnova un account con scadenza (es. abbonamento mensile)
sbt "backend/runMain decoder.backend.cli.InvitaUtente utente@esempio.it password123 1 5"
#                                                        email             password   mesi limiteMensile

# Account staff/admin: nessuna scadenza, quota illimitata
sbt "backend/runMain decoder.backend.cli.InvitaUtente admin@esempio.it password123 illimitato"

# Abilita il 2FA su un account già esistente (stampa un QR ASCII da scansionare)
sbt "backend/runMain decoder.backend.cli.Abilita2fa utente@esempio.it"

# Richieste di accesso arrivate dal form "Registrati"
sbt "backend/runMain decoder.backend.cli.GestisciRichieste lista"
sbt "backend/runMain decoder.backend.cli.GestisciRichieste approva utente@esempio.it 1 5"
sbt "backend/runMain decoder.backend.cli.GestisciRichieste rifiuta utente@esempio.it"
```

## Archivio fonti (RAG)

Archivio di documenti di riferimento (leggi, report, fonti primarie) caricati
manualmente da un admin, self-hosted su Postgres/pgvector, usato dal motore
di analisi come contesto aggiuntivo (RAG — Retrieval-Augmented Generation).
Pensato per non dipendere dalla ricerca live sul web per le fonti citate: i
documenti restano indicizzati sul proprio server, al riparo da rimozioni o
censura altrove.

**Requisiti**: `OLLAMA_BASE_URL` configurato (Ollama self-hosted, o un
gateway compatibile OpenAI come [OmniRoute](https://github.com/diegosouzapw/OmniRoute)
davanti a Ollama) — gli embeddings per il RAG passano sempre di lì,
indipendentemente da quale `AI_PROVIDER` è scelto per l'analisi vera e
propria. Postgres deve avere l'estensione `pgvector` disponibile (vedi nota
Coolify più sotto).

```bash
# Carica un documento (PDF o .txt) nell'archivio fonti
sbt "backend/runMain decoder.backend.cli.IngestaFonte /percorso/documento.pdf 'Titolo del documento'"
```

Ad ogni analisi, `Fonti.contestoPer` cerca (cosine similarity) i chunk più
pertinenti al testo analizzato e li inietta nel prompt come contesto
citabile. Se `OLLAMA_BASE_URL` non è configurato, o l'archivio è vuoto, o gli
embeddings falliscono, l'analisi prosegue comunque senza RAG (mai un
requisito bloccante).

## Deploy (Hetzner + Coolify)

Coolify (self-hosted, open-source) su una VPS Hetzner. Due opzioni:

1. **App singola + Postgres gestito da Coolify**: creare in Coolify una
   risorsa "Dockerfile" puntata a questo repo, e separatamente un servizio
   Postgres (template Coolify); impostare `DATABASE_URL` con l'host interno
   fornito da Coolify. Tutte le altre variabili da `.env.example` si
   impostano nella UI di Coolify (mai committarle nel repo).
2. **Docker Compose**: creare una risorsa "Docker Compose" puntata a
   `docker-compose.yml` — include già il servizio Postgres con volume
   persistente.

In entrambi i casi, dopo il primo deploy creare almeno un account admin con
`InvitaUtente` (via shell del container, o sbt in locale puntato al
Postgres di produzione).

> **Importante per il RAG**: la migration `V2__fonti_pgvector.sql` richiede
> l'estensione Postgres `vector` (pgvector). Il servizio Postgres "template
> Coolify" standard non la include. Con l'opzione 1, va creato un servizio
> Postgres custom in Coolify basato sull'immagine `pgvector/pgvector:pg16`
> (stesso comportamento di un Postgres normale, estensione già installata)
> invece del template Postgres di default. Con l'opzione 2 non serve fare
> nulla: `docker-compose.yml` usa già `pgvector/pgvector:pg16`. Se questa
> migration viene eseguita contro un Postgres senza pgvector, fallisce
> all'avvio (`CREATE EXTENSION vector` va in errore) e va risolto prima che
> l'app possa ripartire.

## Sostenibilità dei costi

`AI_PROVIDER` sceglie il motore di analisi:

- **`anthropic`** — qualità migliore, prompt tarato su Claude; il testo
  analizzato viene inviato ad Anthropic.
- **`openrouter`** — modelli open-source via terzi, pay-per-token.
- **`ollama`** — modello open-source autogestito sulla propria VM: nessun
  costo per token, massima privacy (il testo non lascia mai
  l'infrastruttura), ma richiede risorse (GPU/CPU) dedicate per l'inferenza.

## Trade-off consapevoli

- **JWT in `sessionStorage`, non cookie httpOnly**: più semplice da
  implementare senza CSRF token, ma esposto a XSS se mai ne comparisse uno
  nel frontend. "Logout" è solo "il client dimentica il token" — non esiste
  un modo per invalidare lato server un JWT già emesso prima della scadenza
  (30 giorni). Accettabile per uno strumento ad accesso riservato via
  whitelist, non per un'app multi-tenant pubblica.
- **Cap dimensione articolo post-hoc, non streaming**: `EstraiArticolo`
  scarica l'intero corpo prima di controllarne la dimensione, invece di
  troncare in streaming. Scelta deliberata per non aggiungere una
  dipendenza fs2/zio-streams per un solo controllo difensivo.
- **Nessuna pipeline di test automatici**: la verifica avviene al build
  (compilazione completa nel `Dockerfile`) e con prove manuali in produzione.
  Per un progetto di queste dimensioni e con un solo manutentore è una scelta
  di costo/beneficio, non un ideale.
- **Analisi in una sola richiesta HTTP**: l'analisi può durare un paio di
  minuti e viene restituita in un'unica risposta. Da telefono la connessione
  può cadere se lo schermo si spegne o si cambia app. Il passo successivo
  naturale sarebbe un'esecuzione in background con controllo periodico del
  risultato.
