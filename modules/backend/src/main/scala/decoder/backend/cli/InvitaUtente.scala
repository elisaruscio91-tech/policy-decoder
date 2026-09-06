package decoder.backend.cli

import decoder.backend.config.AppConfig
import decoder.backend.db.{Db, Repo, WhitelistEntry}
import org.mindrot.jbcrypt.BCrypt
import zio.*

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import java.util.UUID

// Crea o rinnova un account (whitelist + password) — porta 1:1 di
// scripts/invita-utente.ts. Repo.upsertWhitelist non tocca mai
// id/totpSecret/createdAt in caso di conflitto (vedi commento lì), quindi qui
// vengono generati solo per il caso "nuovo inserimento" e ignorati altrimenti.
//
// Uso:
//   sbt "backend/runMain decoder.backend.cli.InvitaUtente <email> <password> [mesi=1] [limiteMensile=5]"
//   sbt "backend/runMain decoder.backend.cli.InvitaUtente <email> <password> illimitato"   (staff/admin:
//                                                          nessuna scadenza, nessuna quota)
object InvitaUtente extends ZIOAppDefault:

  private def hashPassword(password: String): Task[String] =
    ZIO.attemptBlocking(BCrypt.hashpw(password, BCrypt.gensalt(12)))

  private def esegui(args: List[String]): RIO[Repo, Unit] =
    args match
      case email :: password :: resto =>
        val emailNorm = email.toLowerCase.trim
        for
          hash <- hashPassword(password)
          repo <- ZIO.service[Repo]
          _ <- resto match
            case "illimitato" :: Nil =>
              val entry = WhitelistEntry(
                id = UUID.randomUUID().toString,
                email = emailNorm,
                passwordHash = Some(hash),
                totpSecret = None,
                fonte = "staff",
                scadenza = None,
                limiteMensile = -1,
                createdAt = Instant.now(),
              )
              repo.upsertWhitelist(entry) *>
                Console.printLine(s"OK: $emailNorm admin/staff — nessuna scadenza, quota illimitata")
            case _ =>
              val mesi = resto.headOption.flatMap(_.toIntOption).getOrElse(1)
              val limiteMensile = resto.drop(1).headOption.flatMap(_.toIntOption).getOrElse(5)
              val scadenza = ZonedDateTime.now(ZoneOffset.UTC).plusMonths(mesi.toLong).toInstant
              val entry = WhitelistEntry(
                id = UUID.randomUUID().toString,
                email = emailNorm,
                passwordHash = Some(hash),
                totpSecret = None,
                fonte = "abbonamento",
                scadenza = Some(scadenza),
                limiteMensile = limiteMensile,
                createdAt = Instant.now(),
              )
              repo.upsertWhitelist(entry) *>
                Console.printLine(
                  s"OK: $emailNorm attivo fino al ${scadenza.toString.take(10)} ($limiteMensile analisi/mese)",
                )
        yield ()
      case _ =>
        Console.printLine("Uso: <email> <password> [mesi=1] [limiteMensile=5]") *>
          Console.printLine("     <email> <password> illimitato   (admin/staff)") *>
          ZIO.fail(new IllegalArgumentException("argomenti mancanti"))

  override def run =
    for
      args <- getArgs
      _ <- esegui(args.toList).provide(AppConfig.layer, Db.dataSourceLayer, Db.quillLayer, Repo.layer)
    yield ()
