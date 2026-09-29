package decoder.frontend

import decoder.shared.*
import org.scalajs.dom
import sttp.client3.*
import sttp.model.{StatusCode, Uri}
import sttp.tapir.client.sttp.SttpClientInterpreter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Client HTTP verso le stesse definizioni Tapir usate dal backend (Endpoints,
// in modules/shared): un solo posto dove path/metodi/shape del JSON sono
// tenuti sincronizzati. Nessun cookie: il JWT è tenuto in sessionStorage e
// rimandato via header "Authorization: Bearer <token>" (vedi Endpoints.scala
// per il trade-off rispetto a un cookie httpOnly). "Logout" è quindi solo
// "dimentica il token lato client": nessuna chiamata al server.
object Api:

  private val backend: SttpBackend[Future, Any] = FetchBackend()
  private val interpreter = SttpClientInterpreter()

  // Stesso host/porta della pagina che serve il frontend: un solo container,
  // niente CORS (vedi Main.scala lato backend).
  private val baseUri: Option[Uri] = Uri.parse(dom.window.location.origin).toOption

  private val TOKEN_KEY = "policy-decoder-token"
  private val EMAIL_KEY = "policy-decoder-email"

  // sttp3 applica un readTimeout di default di 60s su ogni richiesta: troppo
  // poco per /api/analyze, che può richiedere fino a ~180s lato server per
  // l'elaborazione IA. Senza questo override il browser annulla la fetch
  // (visibile come "(canceled)" nel Network tab) anche quando il server sta
  // ancora lavorando e risponderebbe correttamente.
  private val TIMEOUT_ANALISI = scala.concurrent.duration.Duration(200, java.util.concurrent.TimeUnit.SECONDS)

  def leggiToken(): Option[String] = Option(dom.window.sessionStorage.getItem(TOKEN_KEY))
  def salvaToken(token: String): Unit = dom.window.sessionStorage.setItem(TOKEN_KEY, token)
  def rimuoviToken(): Unit = dom.window.sessionStorage.removeItem(TOKEN_KEY)

  def leggiEmail(): Option[String] = Option(dom.window.sessionStorage.getItem(EMAIL_KEY))
  def salvaEmail(email: String): Unit = dom.window.sessionStorage.setItem(EMAIL_KEY, email)
  def rimuoviEmail(): Unit = dom.window.sessionStorage.removeItem(EMAIL_KEY)

  // Endpoints.login/analyze/fetchUrl includono un input "ip" (extractFromRequest,
  // popolato lato server dall'header X-Forwarded-For): non ha rappresentazione
  // sul wire, ma il client generato richiede comunque un valore in quella
  // posizione — passiamo una stringa vuota, viene ignorata.
  def login(req: LoginRequest): Future[Either[(StatusCode, ErroreApi), LoginResponse]] =
    interpreter
      .toRequestThrowDecodeFailures(Endpoints.login, baseUri)
      .apply((req, ""))
      .send(backend)
      .map(_.body)

  def richiediAccesso(req: RichiediAccessoRequest): Future[Either[(StatusCode, ErroreApi), MessaggioOk]] =
    interpreter
      .toRequestThrowDecodeFailures(Endpoints.richiediAccesso, baseUri)
      .apply((req, ""))
      .send(backend)
      .map(_.body)

  def usage(token: String): Future[Either[(StatusCode, ErroreApi), Quota]] =
    interpreter
      .toSecureRequestThrowDecodeFailures(Endpoints.usage, baseUri)
      .apply(token)
      .apply(())
      .send(backend)
      .map(_.body)

  def analyze(token: String, req: AnalyzeRequest): Future[Either[(StatusCode, ErroreApi), AnalyzeResponse]] =
    interpreter
      .toSecureRequestThrowDecodeFailures(Endpoints.analyze, baseUri)
      .apply(token)
      .apply((req, ""))
      .readTimeout(TIMEOUT_ANALISI)
      .send(backend)
      .map(_.body)

  def fetchUrl(token: String, req: FetchUrlRequest): Future[Either[(StatusCode, ErroreApi), Articolo]] =
    interpreter
      .toSecureRequestThrowDecodeFailures(Endpoints.fetchUrl, baseUri)
      .apply(token)
      .apply((req, ""))
      .send(backend)
      .map(_.body)

  def exportPdf(token: String, req: ExportPdfRequest): Future[Either[(StatusCode, ErroreApi), Array[Byte]]] =
    interpreter
      .toSecureRequestThrowDecodeFailures(Endpoints.exportPdf, baseUri)
      .apply(token)
      .apply(req)
      .send(backend)
      .map(_.body.map { case (bytes, _, _) => bytes })
