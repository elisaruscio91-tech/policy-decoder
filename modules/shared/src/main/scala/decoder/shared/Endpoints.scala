package decoder.shared

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

// Definizioni Tapir condivise: JVM (backend, le implementa) e JS (frontend,
// genera un client sttp da queste stesse definizioni — un solo posto dove
// tenere sincronizzati path, metodi e shape del JSON).
//
// Autenticazione: JWT via header "Authorization: Bearer <token>" (securityIn
// auth.bearer). Nessun cookie e nessun endpoint di logout lato server: il
// token è stateless, il logout è solo "il frontend lo dimentica" (vedi
// frontend/Api.scala). Trade-off consapevole rispetto a un cookie httpOnly —
// vedi README.
//
// L'IP del chiamante è estratto dall'header "X-Forwarded-For" con
// extractFromRequest: trasparente per il client generato (non richiede di
// passare nulla esplicitamente).
object Endpoints:

  private val apiBase = "api"
  private type Err = (StatusCode, ErroreApi)

  private val ip: EndpointInput[String] =
    extractFromRequest(req =>
      req.header("X-Forwarded-For").flatMap(_.split(",").headOption).map(_.trim).getOrElse("sconosciuto"),
    )

  val login =
    endpoint.post
      .in(apiBase / "auth" / "login")
      .in(jsonBody[LoginRequest])
      .in(ip)
      .out(jsonBody[LoginResponse])
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  // Form "Registrati": pubblico (nessun bearer, l'utente non ha ancora un
  // account), crea una riga in richiesta_accesso in stato "in_attesa" —
  // l'attivazione vera e propria è manuale (vedi GestisciRichieste, CLI).
  val richiediAccesso =
    endpoint.post
      .in(apiBase / "auth" / "richiedi-accesso")
      .in(jsonBody[RichiediAccessoRequest])
      .in(ip)
      .out(jsonBody[MessaggioOk])
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  val usage =
    endpoint.get
      .securityIn(auth.bearer[String]())
      .in(apiBase / "usage")
      .out(jsonBody[Quota])
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  val analyze =
    endpoint.post
      .securityIn(auth.bearer[String]())
      .in(apiBase / "analyze")
      .in(jsonBody[AnalyzeRequest])
      .in(ip)
      .out(jsonBody[AnalyzeResponse])
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  val fetchUrl =
    endpoint.post
      .securityIn(auth.bearer[String]())
      .in(apiBase / "fetch-url")
      .in(jsonBody[FetchUrlRequest])
      .in(ip)
      .out(jsonBody[Articolo])
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  // Genera un PDF lato server a partire da un risultato che il client ha già
  // ricevuto: niente nuova chiamata all'IA, solo formattazione. Output binario
  // con Content-Type/Content-Disposition impostati esplicitamente dalla logica
  // server (vedi EndpointsImpl) così il browser propone subito il salvataggio
  // del file col nome giusto invece di mostrarlo inline.
  val exportPdf =
    endpoint.post
      .securityIn(auth.bearer[String]())
      .in(apiBase / "analyze" / "pdf")
      .in(jsonBody[ExportPdfRequest])
      .out(byteArrayBody)
      .out(header[String]("Content-Type"))
      .out(header[String]("Content-Disposition"))
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  // Tab "Documento": il client manda i bytes grezzi del PDF scelto dall'utente
  // (niente JSON/base64, per evitare il +33% di dimensione che comporterebbe)
  // più il nome del file in un header, perché byteArrayBody da solo non porta
  // metadati. Il backend estrae il testo (vedi EstraiPdf) e lo restituisce
  // come un Articolo, esattamente come fetchUrl: il frontend può trattare i
  // due flussi allo stesso modo prima di passare il testo a /api/analyze.
  val uploadPdf =
    endpoint.post
      .securityIn(auth.bearer[String]())
      .in(apiBase / "upload-pdf")
      .in(header[String]("X-Nome-File"))
      .in(byteArrayBody)
      .in(ip)
      .out(jsonBody[Articolo])
      .errorOut(statusCode.and(jsonBody[ErroreApi]))

  val all: List[AnyEndpoint] = List(login, richiediAccesso, usage, analyze, fetchUrl, exportPdf, uploadPdf)
