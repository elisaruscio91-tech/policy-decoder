package decoder.backend.sicurezza

import decoder.backend.db.Repo
import zio.*

import java.time.Instant

// Porta 1:1 di src/lib/sicurezza.ts: rilevazione brute-force/DoS su login e
// API, log su Postgres (evento_sicurezza), niente servizi esterni aggiuntivi.
object Sicurezza:

  def registraEvento(ip: String, endpoint: String, esito: String): RIO[Repo, Unit] =
    ZIO.serviceWithZIO[Repo](_.registraEvento(ip, endpoint, esito))

  /** True se l'IP ha superato il numero massimo di tentativi sull'endpoint
    * nella finestra temporale indicata (in minuti). Non registra l'evento:
    * chi chiama decide come/se loggarlo (stesso comportamento di prima).
    */
  def superaLimite(ip: String, endpoint: String, limite: Int, finestraMinuti: Int): RIO[Repo, Boolean] =
    ZIO.serviceWithZIO[Repo] { repo =>
      val inizio = Instant.now().minusSeconds(finestraMinuti * 60L)
      repo.contaEventiRecenti(ip, endpoint, inizio).map(_ >= limite)
    }
