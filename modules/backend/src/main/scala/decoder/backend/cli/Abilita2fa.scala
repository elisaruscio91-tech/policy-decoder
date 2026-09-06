package decoder.backend.cli

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import decoder.backend.config.AppConfig
import decoder.backend.db.{Db, Repo}
import decoder.backend.sicurezza.Base32
import zio.*

import java.net.URLEncoder

// Abilita il 2FA (TOTP, RFC 6238) su un account già esistente — porta 1:1 di
// scripts/abilita-2fa.ts. Il QR ASCII è reso modulo-per-modulo con l'API di
// basso livello di ZXing (Encoder.encode + QRCode.getMatrix), non con
// QRCodeWriter: quest'ultima scala la matrice a una risoluzione fissa,
// introducendo artefatti che comprometterebbero la scansione da terminale.
// Qui un modulo = esattamente due caratteri "██" / due spazi.
//
// Uso: sbt "backend/runMain decoder.backend.cli.Abilita2fa <email>"
object Abilita2fa extends ZIOAppDefault:

  private val EMITTENTE = "Policy Decoder"

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  private def otpauthUrl(email: String, secret: String): String =
    s"otpauth://totp/${urlEncode(EMITTENTE)}:${urlEncode(email)}" +
      s"?secret=$secret&issuer=${urlEncode(EMITTENTE)}&algorithm=SHA1&digits=6&period=30"

  private def stampaQr(testo: String): Task[Unit] = ZIO.attempt {
    val qr = Encoder.encode(testo, ErrorCorrectionLevel.M)
    val matrice = qr.getMatrix
    val sb = new StringBuilder
    for y <- 0 until matrice.getHeight do
      for x <- 0 until matrice.getWidth do
        sb.append(if matrice.get(x, y) != 0 then "██" else "  ")
      sb.append('\n')
    print(sb.toString)
  }

  private def esegui(args: List[String]): RIO[Repo, Unit] =
    args match
      case email :: Nil =>
        val emailNorm = email.toLowerCase.trim
        for
          repo    <- ZIO.service[Repo]
          voceOpt <- repo.trovaWhitelist(emailNorm)
          _ <- voceOpt match
            case None =>
              Console.printLine(
                s"Nessun account trovato per $emailNorm. Crealo prima con: InvitaUtente $emailNorm <password>",
              ) *> ZIO.fail(new IllegalArgumentException("account non trovato"))
            case Some(_) =>
              val secret = Base32.generateSecret()
              for
                _ <- repo.abilitaTotp(emailNorm, secret)
                _ <- Console.printLine(s"2FA abilitata per $emailNorm.")
                _ <- Console.printLine("Scansiona questo QR con la tua app authenticator:\n")
                _ <- stampaQr(otpauthUrl(emailNorm, secret))
                _ <- Console.printLine(s"\nSe non riesci a scansionare, inserisci manualmente questo codice: $secret")
              yield ()
        yield ()
      case _ =>
        Console.printLine("Uso: <email>") *> ZIO.fail(new IllegalArgumentException("argomenti mancanti"))

  override def run =
    for
      args <- getArgs
      _ <- esegui(args.toList).provide(AppConfig.layer, Db.dataSourceLayer, Db.quillLayer, Repo.layer)
    yield ()
