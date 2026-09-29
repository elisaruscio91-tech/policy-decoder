package decoder.backend.db

import java.time.Instant

// Rispecchiano i tre modelli Prisma della versione precedente (vedi
// prisma/schema.prisma nella storia del progetto): stessi campi, stesso
// significato. Nomi di colonna in snake_case (naming strategy di Quill).

final case class WhitelistEntry(
  id: String,
  email: String,
  passwordHash: Option[String],
  totpSecret: Option[String],
  fonte: String,
  scadenza: Option[Instant],
  limiteMensile: Int,
  createdAt: Instant,
)

final case class EventoSicurezza(
  id: String,
  ip: String,
  endpoint: String,
  esito: String,
  createdAt: Instant,
)

final case class RichiestaAccesso(
  id: String,
  email: String,
  passwordHash: String,
  stato: String, // "in_attesa" | "approvata" | "rifiutata"
  createdAt: Instant,
  gestitaAt: Option[Instant],
)

final case class AnalisiRichiesta(
  id: String,
  userId: String,
  modalita: String,
  lunghezzaInput: Int,
  dangerScore: Option[Int],
  esito: String,
  erroreMessaggio: Option[String],
  durataMs: Option[Int],
  createdAt: Instant,
)
