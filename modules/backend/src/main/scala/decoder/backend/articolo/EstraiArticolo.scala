package decoder.backend.articolo

import decoder.backend.http.HttpClient
import decoder.shared.Articolo
import net.dankito.readability4j.Readability4J
import sttp.client3.*
import sttp.model.Uri
import zio.{RIO, Task, ZIO}

import java.net.{InetAddress, URI as JUri}
import scala.concurrent.duration.*

// Estrazione testo articolo lato server: niente chiamate al motore IA per il
// semplice recupero della pagina (risparmio di token e di costo). Include
// protezioni anti-SSRF: solo http/https, niente IP privati/loopback/link-
// local (anche dopo risoluzione DNS e ad ogni redirect seguito a mano).
// Porta 1:1 di src/lib/estrai-articolo.ts.
object EstraiArticolo:

  final case class ErroreArticolo(messaggio: String) extends Exception(messaggio)

  // Il sito ha attivamente rifiutato la richiesta (bot-detection, blocco IP
  // datacenter, ecc.) — distinto da ErroreArticolo perché EndpointsImpl deve
  // tradurlo in un messaggio bilingue dedicato invece di un errore generico.
  final case class SitoBloccatoException(codice: Int) extends Exception(s"Il sito ha bloccato la richiesta automatica ($codice)")

  private val MAX_BYTES     = 3 * 1024 * 1024 // 3MB
  private val TIMEOUT       = 10.seconds
  private val MAX_REDIRECTS = 3
  private val USER_AGENT    = "Mozilla/5.0 (compatible; PolicyDecoderBot/1.0; +https://associazionetransgenere.org)"
  private val CODICI_REDIRECT = Set(301, 302, 303, 307, 308)
  // Codici tipici di bot-detection / blocco geografico o IP: il sito ha
  // ricevuto la richiesta ma l'ha rifiutata attivamente, a differenza di un
  // 404/500 generico.
  private val CODICI_BLOCCO = Set(401, 403, 429, 451)
  // Servizio pubblico di estrazione testo (r.jina.ai/<url originale>): usato
  // come fallback quando il sito blocca la richiesta diretta dal nostro
  // server — la richiesta parte dai loro server, non dal nostro, quindi
  // aggira il blocco IP/bot-detection senza bisogno di mascherare nulla da
  // parte nostra. Nessun rischio SSRF: l'host è fisso (r.jina.ai) e l'URL
  // originale è già stato validato da validaUrlSicuro prima di arrivare qui.
  private val JINA_READER_PREFIX = "https://r.jina.ai/"

  private def ipPrivato(addr: InetAddress): Boolean =
    addr match
      case _: java.net.Inet4Address =>
        val parti = addr.getHostAddress.split("\\.").map(_.toInt)
        val a = parti(0)
        val b = parti(1)
        a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
      case _: java.net.Inet6Address =>
        val l = addr.getHostAddress.toLowerCase
        l == "::1" || l.startsWith("fc") || l.startsWith("fd") || l.startsWith("fe80")
      case _ => true

  private def validaUrlSicuro(url: JUri): Task[Unit] =
    if url.getScheme != "http" && url.getScheme != "https" then ZIO.fail(ErroreArticolo("Protocollo non consentito"))
    else if url.getHost == null then ZIO.fail(ErroreArticolo("URL non valido"))
    else if url.getHost == "localhost" then ZIO.fail(ErroreArticolo("Host non consentito"))
    else
      ZIO
        .attemptBlocking(InetAddress.getAllByName(url.getHost))
        .mapError(_ => ErroreArticolo("Impossibile risolvere l'host"))
        .flatMap { indirizzi =>
          if indirizzi.exists(ipPrivato) then ZIO.fail(ErroreArticolo("L'host risolve a un indirizzo non consentito"))
          else ZIO.unit
        }

  private def gestisciRisposta(
    backend: HttpClient.Backend,
    url: JUri,
    redirectsRimasti: Int,
    resp: Response[String],
  ): Task[(JUri, String)] =
    val codice = resp.code.code
    if CODICI_REDIRECT.contains(codice) then
      resp.header("location") match
        case Some(location) if redirectsRimasti > 0 =>
          ZIO.attempt(url.resolve(location)).flatMap(loop(backend, _, redirectsRimasti - 1))
        case _ =>
          ZIO.fail(ErroreArticolo("Troppi redirect o redirect mancante"))
    else if CODICI_BLOCCO.contains(codice) then ZIO.fail(SitoBloccatoException(codice))
    else if !resp.code.isSuccess then ZIO.fail(ErroreArticolo(s"Impossibile recuperare la pagina ($codice)"))
    else
      val contentType = resp.header("content-type").getOrElse("")
      if !contentType.contains("text/html") && !contentType.contains("application/xhtml") then
        ZIO.fail(ErroreArticolo("Il contenuto non è una pagina HTML"))
      else if resp.body.getBytes("UTF-8").length > MAX_BYTES then ZIO.fail(ErroreArticolo("Pagina troppo grande"))
      else ZIO.succeed((url, resp.body))

  private def loop(backend: HttpClient.Backend, url: JUri, redirectsRimasti: Int): Task[(JUri, String)] =
    for
      _ <- validaUrlSicuro(url)
      uriSttp <- ZIO.fromEither(Uri.parse(url.toString)).mapError(e => ErroreArticolo(s"URL non valido: $e"))
      resp <- basicRequest
        .get(uriSttp)
        .followRedirects(false)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html")
        .readTimeout(TIMEOUT)
        .response(asStringAlways)
        .send(backend)
      risultato <- gestisciRisposta(backend, url, redirectsRimasti, resp)
    yield risultato

  // Jina restituisce già testo pulito (markdown semplice, tipicamente con un
  // "Title: ..." in cima) — niente Readability4J qui, l'HTML non c'entra.
  private def estraiViaFallback(url: JUri, backend: HttpClient.Backend): Task[Articolo] =
    for
      uriFallback <- ZIO
        .fromEither(Uri.parse(JINA_READER_PREFIX + url.toString))
        .mapError(e => ErroreArticolo(s"URL fallback non valido: $e"))
      resp <- basicRequest
        .get(uriFallback)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/plain")
        .readTimeout(TIMEOUT)
        .response(asStringAlways)
        .send(backend)
      _ <- ZIO.when(!resp.code.isSuccess)(
        ZIO.fail(ErroreArticolo(s"Servizio di lettura alternativo non disponibile (${resp.code.code})")),
      )
      _ <- ZIO.when(resp.body.getBytes("UTF-8").length > MAX_BYTES)(ZIO.fail(ErroreArticolo("Pagina troppo grande")))
      testo = resp.body.trim
      _ <- ZIO.when(testo.isEmpty)(ZIO.fail(ErroreArticolo("Servizio di lettura alternativo ha restituito una pagina vuota")))
    yield
      val titolo = testo.linesIterator
        .find(_.trim.nonEmpty)
        .map(_.stripPrefix("Title:").trim)
        .filter(_.nonEmpty)
        .getOrElse(url.getHost)
      Articolo(titolo = titolo, testo = testo, fonte = url.toString)

  private def estraiConReadability(url: JUri, html: String): Task[Articolo] =
    ZIO.attemptBlocking {
      val readability = new Readability4J(url.toString, html)
      val articolo    = readability.parse()
      Option(articolo.getTextContent).map(_.trim).filter(_.nonEmpty) match
        case None => throw ErroreArticolo("Impossibile estrarre il contenuto dell'articolo da questa pagina")
        case Some(testo) =>
          Articolo(
            titolo = Option(articolo.getTitle).filter(_.nonEmpty).getOrElse(url.getHost),
            testo = testo,
            fonte = url.toString,
          )
    }

  def estraiArticolo(urlInput: String): RIO[HttpClient.Backend, Articolo] =
    for
      backend     <- ZIO.service[HttpClient.Backend]
      urlIniziale <- ZIO.attempt(new JUri(urlInput)).mapError(_ => ErroreArticolo("URL non valido"))
      articolo <- loop(backend, urlIniziale, MAX_REDIRECTS)
        .flatMap { case (urlFinale, html) => estraiConReadability(urlFinale, html) }
        .catchSome { case _: SitoBloccatoException => estraiViaFallback(urlIniziale, backend) }
    yield articolo
