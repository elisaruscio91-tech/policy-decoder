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

  val all: List[AnyEndpoint] = List(login, usage, analyze, fetchUrl)
