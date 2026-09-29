package decoder.backend.db

import io.getquill.*
import io.getquill.extras.InstantOps
import zio.*

import java.time.Instant
import java.util.UUID

// Query Quill centralizzate: un solo posto dove sono definite le shape delle
// tabelle (querySchema), usato sia dai servizi del server sia dagli script CLI.
// `ctx` è già l'istanza concreta di Quill.Postgres (con dentro il pool JDBC),
// quindi `run(...)` qui produce direttamente un Task, senza bisogno di altro
// ambiente a valle: chi usa Repo non deve preoccuparsi di Db.Ctx.
final class Repo(ctx: Db.Ctx):
  import ctx.*

  inline def whitelistEntries = querySchema[WhitelistEntry]("whitelist_entry")
  inline def eventiSicurezza = querySchema[EventoSicurezza]("evento_sicurezza")
  inline def analisiRichieste = querySchema[AnalisiRichiesta]("analisi_richiesta")
  inline def richiesteAccesso = querySchema[RichiestaAccesso]("richiesta_accesso")

  def trovaWhitelist(email: String): Task[Option[WhitelistEntry]] =
    run(whitelistEntries.filter(_.email == lift(email))).map(_.headOption)

  // Non tocca mai totpSecret in conflitto: invitare/rinnovare un account non
  // deve disabilitare il 2FA già configurato (stesso comportamento dell'upsert
  // Prisma originale in scripts/invita-utente.ts). Per il 2FA vedi abilitaTotp.
  def upsertWhitelist(entry: WhitelistEntry): Task[WhitelistEntry] =
    run {
      whitelistEntries
        .insertValue(lift(entry))
        .onConflictUpdate(_.email)(
          (t, e) => t.passwordHash -> e.passwordHash,
          (t, e) => t.fonte -> e.fonte,
          (t, e) => t.scadenza -> e.scadenza,
          (t, e) => t.limiteMensile -> e.limiteMensile,
        )
    }.as(entry)

  def abilitaTotp(email: String, secret: String): Task[Long] =
    run(whitelistEntries.filter(_.email == lift(email)).update(_.totpSecret -> lift(Option(secret))))

  def registraEvento(ip: String, endpoint: String, esito: String): Task[Unit] =
    run {
      eventiSicurezza.insertValue(
        lift(EventoSicurezza(UUID.randomUUID().toString, ip, endpoint, esito, Instant.now())),
      )
    }.unit

  def contaEventiRecenti(ip: String, endpoint: String, dopo: Instant): Task[Long] =
    run {
      eventiSicurezza
        .filter(e => e.ip == lift(ip) && e.endpoint == lift(endpoint) && e.createdAt >= lift(dopo))
        .size
    }

  def registraAnalisi(a: AnalisiRichiesta): Task[Unit] =
    run(analisiRichieste.insertValue(lift(a))).unit

  def analisiUsateDaInizioMese(userId: String, inizioMese: Instant): Task[Long] =
    run {
      analisiRichieste
        .filter(a => a.userId == lift(userId) && a.esito == lift("successo") && a.createdAt >= lift(inizioMese))
        .size
    }

  def inserisciRichiestaAccesso(r: RichiestaAccesso): Task[Unit] =
    run(richiesteAccesso.insertValue(lift(r))).unit

  def trovaRichiestaPendente(email: String): Task[Option[RichiestaAccesso]] =
    run(richiesteAccesso.filter(r => r.email == lift(email) && r.stato == lift("in_attesa"))).map(_.headOption)

  def listaRichiestePendenti(): Task[List[RichiestaAccesso]] =
    run(richiesteAccesso.filter(_.stato == lift("in_attesa")))

  def segnaRichiesta(id: String, stato: String, gestitaAt: Instant): Task[Long] =
    run(richiesteAccesso.filter(_.id == lift(id)).update(_.stato -> lift(stato), _.gestitaAt -> lift(Option(gestitaAt))))

object Repo:
  val layer: URLayer[Db.Ctx, Repo] = ZLayer.fromFunction(Repo(_))
