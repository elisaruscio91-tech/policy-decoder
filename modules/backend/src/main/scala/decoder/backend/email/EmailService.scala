package decoder.backend.email

import decoder.backend.config.EmailCfg
import decoder.backend.http.HttpClient
import io.circe.Json
import sttp.client3.*
import zio.*

// Invio email transazionali via Resend (https://resend.com), API HTTP
// semplice: niente nuova dipendenza sbt, solo una richiesta POST via il
// backend sttp già condiviso col resto del server (HttpClient.Backend).
// Costruiamo il JSON a mano con io.circe.Json (già sul classpath transitivo
// via modules/shared, che dipende da circe-core/circe-generic) invece di
// aggiungere la dipendenza "sttp-client3-circe": un import in meno da
// verificare senza compilatore locale.
object EmailService:

  private def corpo(cfg: EmailCfg, to: String, subject: String, html: String): Json =
    Json.obj(
      "from"    -> Json.fromString(cfg.from),
      "to"      -> Json.arr(Json.fromString(to)),
      "subject" -> Json.fromString(subject),
      "html"    -> Json.fromString(html),
    )

  /** Invio best-effort: un problema con Resend (chiave assente, dominio non
    * verificato, rate limit, timeout) non deve mai far fallire il chiamante
    * (registrazione, login, approvazione) — solo un log di warning.
    */
  def invia(cfg: EmailCfg, to: String, subject: String, html: String): RIO[HttpClient.Backend, Unit] =
    if cfg.resendApiKey.isEmpty then
      ZIO.logWarning(s"EmailService: RESEND_API_KEY assente, email a $to non inviata (oggetto: $subject)")
    else
      val invio: RIO[HttpClient.Backend, Unit] =
        ZIO.serviceWithZIO[HttpClient.Backend] { backend =>
          basicRequest
            .post(uri"https://api.resend.com/emails")
            .header("Authorization", s"Bearer ${cfg.resendApiKey}")
            .contentType("application/json")
            .body(corpo(cfg, to, subject, html).noSpaces)
            .response(asStringAlways)
            .send(backend)
            .flatMap { resp =>
              if resp.code.isSuccess then ZIO.unit
              else ZIO.logWarning(s"EmailService: invio a $to fallito (${resp.code}): ${resp.body}")
            }
        }
      invio.catchAll(errore => ZIO.logWarning(s"EmailService: eccezione inviando a $to: ${errore.getMessage}"))
