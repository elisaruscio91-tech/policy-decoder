# Policy Decoder

Strumento di analisi testi (propaganda, retorica, fact-check) a supporto della
tutela dei diritti delle persone transgender e non binarie. Login riservato
(whitelist, niente registrazione self-service), quota mensile per utente,
motore di analisi configurabile (Anthropic, OpenRouter, o Ollama self-hosted).

Riscrittura completa in **Scala 3 + ZIO + Tapir + Laminar (ScalaJS) + Quill**
della versione precedente (Next.js + Prisma): stessa logica di business,
stack più semplice da mantenere ed eseguire come un singolo processo JVM.
Nessuna pretesa estetica sul frontend ("minimo minimo", vedi sotto) — il
valore dello strumento è nell'analisi, non nell'interfaccia.

## Indice

- [Architettura](#architettura)
- [File per file](#file-per-file)
- [Sviluppo locale](#sviluppo-locale)
- [CLI di amministrazione](#cli-di-amministrazione)
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
Account creati/rinnovati solo via CLI (`InvitaUtente`/`Abilita2fa`), nessuna
registrazione self-service: lo strumento è ad accesso riservato.

## File per file

### `modules/shared` — contratto condiviso tra backend e frontend

- **`Dto.scala`** — tutti i DTO scambiati via JSON (circe), incluso
  `RisultatoAnalisi` con le 7 sezioni prodotte dal motore di analisi
  (summary, danger_score, rhetoric, factcheck, legal, counter_narrative,
  slides). Stesso contratto JSON già in uso nella versione precedente.
- **`Endpoints.scala`** — le 4 definizioni Tapir (`login`, `usage`, `analyze`,
  `fetchUrl`): un solo posto dove path, metodi HTTP e shape del JSON sono
  tenuti sincronizzati tra backend (che le implementa) e frontend (che ne
  genera un client sttp). L'IP del chiamante è estratto dall'header
  `X-Forwarded-For` via `extractFromRequest` — trasparente sul wire, ma il
  client generato richiede comunque un valore positionale per quell'input
  (vedi `Api.scala`).

### `modules/backend` — server

- **`Main.scala`** — entry point: esegue le migrazioni Flyway, poi avvia
  zio-http servendo sia le rotte API (Tapir) sia i file statici del
  frontend compilato (`resources/static/`, copiati lì durante la build
  Docker). **File più a rischio della riscrittura** (API zio-http cambiate
  più volte tra versioni, non compilabile in questo ambiente): se
  `sbt compile` fallisce, guardare qui per primo.
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
  lingua di output, invio analisi, rendering delle 7 sezioni del risultato.
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

Nessuna registrazione self-service: gli account si creano/rinnovano da riga
di comando.

```bash
# Crea/rinnova un account con scadenza (es. abbonamento mensile)
sbt "backend/runMain decoder.backend.cli.InvitaUtente utente@esempio.it password123 1 5"
#                                                        email             password   mesi limiteMensile

# Account staff/admin: nessuna scadenza, quota illimitata
sbt "backend/runMain decoder.backend.cli.InvitaUtente admin@esempio.it password123 illimitato"

# Abilita il 2FA su un account già esistente (stampa un QR ASCII da scansionare)
sbt "backend/runMain decoder.backend.cli.Abilita2fa utente@esempio.it"
```

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
- **Codice non compiler-verificato in alcune aree**: scritto senza una
  toolchain JVM/Scala locale disponibile. `Main.scala` (API zio-http) e le
  interazioni con la libreria Readability4J sono le aree con maggiore
  incertezza — vedi i commenti nel codice per i dettagli.
