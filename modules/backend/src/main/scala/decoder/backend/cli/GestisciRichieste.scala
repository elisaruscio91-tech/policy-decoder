package decoder.backend.cli

import decoder.backend.config.AppConfig
import decoder.backend.db.{Db, Repo, RichiestaAccesso, WhitelistEntry}
import decoder.backend.email.EmailService
import decoder.backend.http.HttpClient
import zio.*

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import java.util.UUID

// Approva/rifiuta le richieste di accesso self-service (tabella
// richiesta_accesso, popolata dal form "Registrati" nel frontend — vedi
// EndpointsImpl.richiediAccessoLogic). L'approvazione crea la vera riga in
// whitelist_entry riusando l'hash password già salvato nella richiesta
// (nessuna nuova password da comunicare), poi invia una email di conferma.
//
// Uso:
//   sbt "backend/runMain decoder.backend.cli.GestisciRichieste lista"
//   sbt "backend/runMain decoder.backend.cli.GestisciRichieste approva <email> [mesi=1] [limiteMensile=5]"
//   sbt "backend/runMain decoder.backend.cli.GestisciRichieste approva <email> illimitato"
//   sbt "backend/runMain decoder.backend.cli.GestisciRichieste rifiuta <email>"
object GestisciRichieste extends ZIOAppDefault:

  type Env = Repo & AppConfig & HttpClient.Backend

  private def trovaPendenteOFallisci(repo: Repo, email: String): Task[RichiestaAccesso] =
    repo.trovaRichiestaPendente(email.toLowerCase.trim).flatMap {
      case Some(r) => ZIO.succeed(r)
      case None    => ZIO.fail(new IllegalArgumentException(s"Nessuna richiesta in attesa per $email"))
    }

  private def esegui(args: List[String]): RIO[Env, Unit] =
    args match
      case "lista" :: Nil =>
        for
          repo      <- ZIO.service[Repo]
          richieste <- repo.listaRichiestePendenti()
          _ <-
            if richieste.isEmpty then Console.printLine("Nessuna richiesta in attesa.")
            else ZIO.foreachDiscard(richieste)(r => Console.printLine(s"${r.email}  (dal ${r.createdAt.toString.take(16)})"))
        yield ()

      case "approva" :: email :: resto =>
        for
          repo <- ZIO.service[Repo]
          cfg  <- ZIO.service[AppConfig]
          r    <- trovaPendenteOFallisci(repo, email)
          entry = resto match
            case "illimitato" :: Nil =>
              WhitelistEntry(
                id = UUID.randomUUID().toString,
                email = r.email,
                passwordHash = Some(r.passwordHash),
                totpSecret = None,
                fonte = "staff",
                scadenza = None,
                limiteMensile = -1,
                createdAt = Instant.now(),
              )
            case _ =>
              val mesi          = resto.headOption.flatMap(_.toIntOption).getOrElse(1)
              val limiteMensile = resto.drop(1).headOption.flatMap(_.toIntOption).getOrElse(5)
              val scadenza      = ZonedDateTime.now(ZoneOffset.UTC).plusMonths(mesi.toLong).toInstant
              WhitelistEntry(
                id = UUID.randomUUID().toString,
                email = r.email,
                passwordHash = Some(r.passwordHash),
                totpSecret = None,
                fonte = "abbonamento",
                scadenza = Some(scadenza),
                limiteMensile = limiteMensile,
                createdAt = Instant.now(),
              )
          _ <- repo.upsertWhitelist(entry)
          _ <- repo.segnaRichiesta(r.id, "approvata", Instant.now())
          _ <- EmailService.invia(
            cfg.email,
            r.email,
            "Richiesta approvata — Policy Decoder",
            "<p>La tua richiesta di accesso a Policy Decoder è stata approvata.</p>" +
              "<p>Puoi ora accedere con l'email e la password che hai scelto in fase di registrazione.</p>",
          )
          _ <- Console.printLine(s"OK: ${r.email} approvato.")
        yield ()

      case "rifiuta" :: email :: Nil =>
        for
          repo <- ZIO.service[Repo]
          cfg  <- ZIO.service[AppConfig]
          r    <- trovaPendenteOFallisci(repo, email)
          _ <- repo.segnaRichiesta(r.id, "rifiutata", Instant.now())
          _ <- EmailService.invia(
            cfg.email,
            r.email,
            "Richiesta non approvata — Policy Decoder",
            "<p>La tua richiesta di accesso a Policy Decoder non è stata approvata.</p>" +
              s"<p>Per informazioni scrivi a ${cfg.email.admin}.</p>",
          )
          _ <- Console.printLine(s"OK: ${r.email} rifiutato.")
        yield ()

      case _ =>
        Console.printLine(
          "Uso: lista | approva <email> [mesi=1] [limiteMensile=5] | approva <email> illimitato | rifiuta <email>",
        ) *> ZIO.fail(new IllegalArgumentException("argomenti mancanti/non validi"))

  override def run =
    for
      args <- getArgs
      _    <- esegui(args.toList).provide(AppConfig.layer, Db.dataSourceLayer, Db.quillLayer, Repo.layer, HttpClient.layer)
    yield ()
