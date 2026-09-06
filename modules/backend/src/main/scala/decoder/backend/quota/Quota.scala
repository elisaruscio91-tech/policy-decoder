package decoder.backend.quota

import decoder.backend.config.AppConfig
import decoder.backend.db.Repo
import decoder.shared.{Quota as QuotaDto}
import zio.*

import java.time.{Instant, LocalDate, ZoneId}

// Porta 1:1 di src/lib/quota.ts: quota mensile per utente, con -1 in
// WhitelistEntry.limiteMensile a significare "nessun limite" (account
// admin/staff).
object Quota:

  // Sentinella per gli account illimitati: un numero enorme (non Infinity,
  // che il JSON non serializza) così i controlli `rimanenti <= 0` restano validi.
  private val RIMANENTI_ILLIMITATO = 999_999

  private def inizioMeseSolareCorrente(): Instant =
    val zona = ZoneId.systemDefault()
    LocalDate.now(zona).withDayOfMonth(1).atStartOfDay(zona).toInstant

  def limiteMensilePer(email: String): RIO[Repo & AppConfig, Int] =
    for
      repo    <- ZIO.service[Repo]
      cfg     <- ZIO.service[AppConfig]
      voceOpt <- repo.trovaWhitelist(email.toLowerCase.trim)
    yield voceOpt.map(_.limiteMensile).getOrElse(cfg.defaultMonthlyQuota)

  def analisiUsateQuestoMese(userId: String): RIO[Repo, Long] =
    ZIO.serviceWithZIO[Repo](_.analisiUsateDaInizioMese(userId, inizioMeseSolareCorrente()))

  def statoQuota(userId: String, email: String): RIO[Repo & AppConfig, QuotaDto] =
    for
      limite <- limiteMensilePer(email)
      usate  <- analisiUsateQuestoMese(userId)
    yield
      if limite < 0 then QuotaDto(-1, usate.toInt, RIMANENTI_ILLIMITATO)
      else QuotaDto(limite, usate.toInt, math.max(0, limite - usate.toInt))
