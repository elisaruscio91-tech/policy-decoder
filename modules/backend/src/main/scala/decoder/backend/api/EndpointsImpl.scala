package decoder.backend.api

import decoder.backend.articolo.EstraiArticolo
import decoder.backend.config.AppConfig
import decoder.backend.db.{AnalisiRichiesta, Repo}
import decoder.backend.fonti.{Fonti, FontiRepo}
import decoder.backend.http.HttpClient
import decoder.backend.ia.{MotoreIA, PromptSistema}
import decoder.backend.quota.Quota
import decoder.backend.sicurezza.{Auth, Sicurezza}
import decoder.shared.{
  AnalyzeRequest, AnalyzeResponse, Articolo, Endpoints, ErroreApi,
  FetchUrlRequest, LoginRequest, LoginResponse, RisultatoAnalisi,
  Quota as QuotaDto,
}
import sttp.model.StatusCode
import sttp.tapir.ztapir.*
import zio.*

import java.time.Instant
import java.util.UUID

// Implementazione delle logiche Tapir: collega le definizioni condivise in
// Endpoints a Auth/Sicurezza/Quota/MotoreIA/EstraiArticolo. Un solo ambiente
// (Env) per tutti gli endpoint, anche quelli che non usano tutte le
// capacità disponibili — evita complicazioni di varianza nel combinare
// endpoint con requisiti diversi in un'unica lista.
object EndpointsImpl:

  type Env = Repo & AppConfig & HttpClient.Backend & FontiRepo

  private val ENDPOINT_ANALYZE = "/api/analyze"
  private val ENDPOINT_FETCH   = "/api/fetch-url"
  private val LIMITE_ANALYZE   = 30
  private val LIMITE_FETCH     = 20
  private val FINESTRA_MINUTI  = 10
  private val MAX_CARATTERI_INPUT = 20_000
  private val LINGUE_VALIDE = Set("auto", "en", "it")

  private case object RateLimitedException extends RuntimeException("Troppe richieste. Riprova tra qualche minuto.")
  private case object CredenzialiNonValideException extends RuntimeException("Credenziali non valide")
  private case object TestoMancanteException extends RuntimeException("Testo mancante")
  private case object UrlMancanteException extends RuntimeException("URL mancante")
  private final case class TestoTroppoLungoException(max: Int)
      extends RuntimeException(s"Testo troppo lungo (max $max caratteri)")
  private final case class QuotaEsauritaException(quota: QuotaDto)
      extends RuntimeException("Quota mensile esaurita. Si rinnova all'inizio del prossimo mese.")
  private final case class AnalisiFallitaException(msg: String) extends RuntimeException(msg)

  private def mappaErrore(e: Throwable): (StatusCode, ErroreApi) = e match
    case RateLimitedException          => (StatusCode.TooManyRequests, ErroreApi(e.getMessage))
    case CredenzialiNonValideException => (StatusCode.Unauthorized, ErroreApi(e.getMessage))
    case TestoMancanteException        => (StatusCode.BadRequest, ErroreApi(e.getMessage))
    case UrlMancanteException          => (StatusCode.BadRequest, ErroreApi(e.getMessage))
    case TestoTroppoLungoException(_)  => (StatusCode.PayloadTooLarge, ErroreApi(e.getMessage))
    case QuotaEsauritaException(quota) => (StatusCode.TooManyRequests, ErroreApi(e.getMessage, Some(quota)))
    case AnalisiFallitaException(msg)  => (StatusCode.BadGateway, ErroreApi(msg))
    case err: EstraiArticolo.ErroreArticolo =>
      (StatusCode.UnprocessableEntity, ErroreApi(Option(err.getMessage).getOrElse("Impossibile recuperare l'articolo")))
    case _ => (StatusCode.InternalServerError, ErroreApi(Option(e.getMessage).getOrElse("Errore interno")))

  private def securityLogic(token: String): ZIO[Env, (StatusCode, ErroreApi), Auth.Utente] =
    for
      cfg    <- ZIO.service[AppConfig]
      utente <- Auth.verificaToken(token, cfg.jwtSecret).mapError(_ => (StatusCode.Unauthorized, ErroreApi("Token non valido o scaduto")))
    yield utente

  private val loginEndpoint: ZServerEndpoint[Env, Any] =
    Endpoints.login.zServerLogic { case (req, ip) =>
      val effetto: RIO[Env, LoginResponse] =
        for
          cfg       <- ZIO.service[AppConfig]
          risultato <- Auth.login(req.email, req.password, req.codice2fa, ip)
          utente <- risultato match
            case Left(Auth.LoginError.RateLimited)          => ZIO.fail(RateLimitedException)
            case Left(Auth.LoginError.CredenzialiNonValide) => ZIO.fail(CredenzialiNonValideException)
            case Right(u)                                   => ZIO.succeed(u)
          token <- Auth.firmaToken(utente, cfg.jwtSecret)
          quota <- Quota.statoQuota(utente.id, utente.email)
        yield LoginResponse(token, utente.email, quota)
      effetto.mapError(mappaErrore)
    }

  private val usageEndpoint: ZServerEndpoint[Env, Any] =
    Endpoints.usage.zServerSecurityLogic(securityLogic).serverLogic { utente => _ =>
      val effetto: RIO[Env, QuotaDto] = Quota.statoQuota(utente.id, utente.email)
      effetto.mapError(mappaErrore)
    }

  private def analyzeLogic(utente: Auth.Utente)(input: (AnalyzeRequest, String)): ZIO[Env, (StatusCode, ErroreApi), AnalyzeResponse] =
    val (req, ip) = input
    val testo      = req.testo.trim
    val modalita   = if req.modalita == "url" then "url" else "testo"
    val outputLang = if LINGUE_VALIDE.contains(req.outputLang) then req.outputLang else "auto"

    val effetto: RIO[Env, AnalyzeResponse] =
      for
        limitato <- Sicurezza.superaLimite(ip, ENDPOINT_ANALYZE, LIMITE_ANALYZE, FINESTRA_MINUTI)
        _ <- ZIO.when(limitato)(
          Sicurezza.registraEvento(ip, ENDPOINT_ANALYZE, "rate-limited") *> ZIO.fail(RateLimitedException),
        )
        _ <- ZIO.when(testo.isEmpty)(
          Sicurezza.registraEvento(ip, ENDPOINT_ANALYZE, "fallimento") *> ZIO.fail(TestoMancanteException),
        )
        _ <- ZIO.when(testo.length > MAX_CARATTERI_INPUT)(
          Sicurezza.registraEvento(ip, ENDPOINT_ANALYZE, "fallimento") *> ZIO.fail(TestoTroppoLungoException(MAX_CARATTERI_INPUT)),
        )
        quota <- Quota.statoQuota(utente.id, utente.email)
        _ <- ZIO.when(quota.rimanenti <= 0)(
          ZIO.serviceWithZIO[Repo](
            _.registraAnalisi(
              AnalisiRichiesta(
                id = UUID.randomUUID().toString,
                userId = utente.id,
                modalita = modalita,
                lunghezzaInput = testo.length,
                dangerScore = None,
                esito = "quota-esaurita",
                erroreMessaggio = None,
                durataMs = None,
                createdAt = Instant.now(),
              ),
            ),
          ) *> Sicurezza.registraEvento(ip, ENDPOINT_ANALYZE, "rate-limited") *> ZIO.fail(QuotaEsauritaException(quota)),
        )
        cfg    <- ZIO.service[AppConfig]
        inizio  = Instant.now()
        risultato <- (
          for
            contesto <- Fonti.contestoPer(testo).catchAll(_ => ZIO.succeed(""))
            raw      <- MotoreIA.chiamaModello(cfg.ai, PromptSistema.build(outputLang), s"Analyze this text:\n\n---\n$testo\n---$contesto")
            parsed   <- MotoreIA.estraiJson[RisultatoAnalisi](raw)
          yield parsed
        ).foldZIO(
          errore =>
            val durataMs = java.time.Duration.between(inizio, Instant.now()).toMillis.toInt
            val msg      = Option(errore.getMessage).getOrElse("Analisi fallita")
            ZIO.serviceWithZIO[Repo](
              _.registraAnalisi(
                AnalisiRichiesta(
                  id = UUID.randomUUID().toString,
                  userId = utente.id,
                  modalita = modalita,
                  lunghezzaInput = testo.length,
                  dangerScore = None,
                  esito = "errore",
                  erroreMessaggio = Some(msg.take(500)),
                  durataMs = Some(durataMs),
                  createdAt = Instant.now(),
                ),
              ),
            ) *> Sicurezza.registraEvento(ip, ENDPOINT_ANALYZE, "fallimento") *> ZIO.fail(AnalisiFallitaException(msg)),
          parsed =>
            val durataMs = java.time.Duration.between(inizio, Instant.now()).toMillis.toInt
            for
              _ <- ZIO.serviceWithZIO[Repo](
                _.registraAnalisi(
                  AnalisiRichiesta(
                    id = UUID.randomUUID().toString,
                    userId = utente.id,
                    modalita = modalita,
                    lunghezzaInput = testo.length,
                    dangerScore = parsed.danger_score.flatMap(_.score),
                    esito = "successo",
                    erroreMessaggio = None,
                    durataMs = Some(durataMs),
                    createdAt = Instant.now(),
                  ),
                ),
              )
              _               <- Sicurezza.registraEvento(ip, ENDPOINT_ANALYZE, "successo")
              quotaAggiornata <- Quota.statoQuota(utente.id, utente.email)
            yield AnalyzeResponse(parsed, quotaAggiornata),
        )
      yield risultato

    effetto.mapError(mappaErrore)

  private def fetchUrlLogic(utente: Auth.Utente)(input: (FetchUrlRequest, String)): ZIO[Env, (StatusCode, ErroreApi), Articolo] =
    val (req, ip) = input
    val url       = req.url.trim

    val effetto: RIO[Env, Articolo] =
      for
        limitato <- Sicurezza.superaLimite(ip, ENDPOINT_FETCH, LIMITE_FETCH, FINESTRA_MINUTI)
        _ <- ZIO.when(limitato)(
          Sicurezza.registraEvento(ip, ENDPOINT_FETCH, "rate-limited") *> ZIO.fail(RateLimitedException),
        )
        _ <- ZIO.when(url.isEmpty)(
          Sicurezza.registraEvento(ip, ENDPOINT_FETCH, "fallimento") *> ZIO.fail(UrlMancanteException),
        )
        articolo <- EstraiArticolo.estraiArticolo(url).foldZIO(
          errore => Sicurezza.registraEvento(ip, ENDPOINT_FETCH, "fallimento") *> ZIO.fail(errore),
          art => Sicurezza.registraEvento(ip, ENDPOINT_FETCH, "successo").as(art),
        )
      yield articolo

    effetto.mapError(mappaErrore)

  private val analyzeEndpoint: ZServerEndpoint[Env, Any] =
    Endpoints.analyze.zServerSecurityLogic(securityLogic).serverLogic(utente => analyzeLogic(utente))

  private val fetchUrlEndpoint: ZServerEndpoint[Env, Any] =
    Endpoints.fetchUrl.zServerSecurityLogic(securityLogic).serverLogic(utente => fetchUrlLogic(utente))

  val all: List[ZServerEndpoint[Env, Any]] = List(loginEndpoint, usageEndpoint, analyzeEndpoint, fetchUrlEndpoint)
