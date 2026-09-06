package decoder.backend.sicurezza

import decoder.backend.db.Repo
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.mindrot.jbcrypt.BCrypt
import zio.*

import java.time.{Duration, Instant}

// Bcrypt (password), TOTP RFC 6238 (2FA opzionale) e JWT (sessione stateless)
// — porta 1:1 della logica in src/lib/auth.ts, senza NextAuth: qui è tutto
// esplicito e sotto il nostro controllo diretto.
object Auth:

  private val ENDPOINT = "/api/auth/login"
  private val LIMITE_TENTATIVI = 5
  private val FINESTRA_MINUTI = 10
  private val TOKEN_VALIDITY = Duration.ofDays(30)
  private val TOTP_STEP = Duration.ofSeconds(30)

  final case class Utente(id: String, email: String)

  enum LoginError:
    case RateLimited, CredenzialiNonValide

  /** Verifica bcrypt in un thread pool dedicato: bcrypt è deliberatamente
    * lento (cost factor 12, stesso valore usato prima in bcryptjs), non deve
    * bloccare l'event loop principale di ZIO.
    */
  private def verificaPassword(password: String, hash: String): UIO[Boolean] =
    ZIO.attemptBlocking(BCrypt.checkpw(password, hash)).orElseSucceed(false)

  /** Tollera ±1 finestra di 30s (stesso comportamento di default di otplib),
    * per assorbire piccoli disallineamenti d'orologio tra client e server.
    */
  private def verificaTotp(codice: String, secretBase32: String): Boolean =
    try
      val gen = new TimeBasedOneTimePasswordGenerator(TOTP_STEP)
      val key = Base32.decodeToKey(secretBase32, gen.getAlgorithm)
      val ora = Instant.now()
      val candidati = Seq(ora.minus(TOTP_STEP), ora, ora.plus(TOTP_STEP))
      candidati.exists(t => gen.generateOneTimePasswordString(key, t) == codice.trim)
    catch case _: Throwable => false

  def firmaToken(utente: Utente, secret: String): Task[String] = ZIO.attempt {
    val claims = new JWTClaimsSet.Builder()
      .subject(utente.id)
      .claim("email", utente.email)
      .expirationTime(java.util.Date.from(Instant.now().plus(TOKEN_VALIDITY)))
      .build()
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims)
    jwt.sign(new MACSigner(secret.getBytes("UTF-8")))
    jwt.serialize()
  }

  def verificaToken(token: String, secret: String): Task[Utente] = ZIO.attempt {
    val jwt = SignedJWT.parse(token)
    if !jwt.verify(new MACVerifier(secret.getBytes("UTF-8"))) then throw new SecurityException("Firma non valida")
    val claims = jwt.getJWTClaimsSet
    if claims.getExpirationTime.before(java.util.Date.from(Instant.now())) then
      throw new SecurityException("Token scaduto")
    Utente(claims.getSubject, claims.getStringClaim("email"))
  }

  /** Login: rate-limit per IP, controllo whitelist+scadenza, bcrypt, TOTP se
    * abilitato. Ogni tentativo (riuscito o no) viene loggato — stesso
    * comportamento di authorize() in src/lib/auth.ts.
    */
  def login(
    email: String,
    password: String,
    codice2fa: Option[String],
    ip: String,
  ): RIO[Repo, Either[LoginError, Utente]] =
    Sicurezza.superaLimite(ip, ENDPOINT, LIMITE_TENTATIVI, FINESTRA_MINUTI).flatMap { limitato =>
      if limitato then
        Sicurezza.registraEvento(ip, ENDPOINT, "rate-limited").as(Left(LoginError.RateLimited))
      else
        val emailNorm = email.toLowerCase.trim
        for
          repo <- ZIO.service[Repo]
          voceOpt <- repo.trovaWhitelist(emailNorm)
          ora = Instant.now()
          attiva = voceOpt.exists(v => v.scadenza.forall(_.isAfter(ora)))
          risultato <- voceOpt match
            case Some(v) if v.passwordHash.isDefined && attiva =>
              for
                passwordOk <- verificaPassword(password, v.passwordHash.get)
                totpOk = v.totpSecret match
                  case Some(secret) => codice2fa.exists(c => c.nonEmpty && verificaTotp(c, secret))
                  case None => true
                ok = passwordOk && totpOk
                _ <- Sicurezza.registraEvento(ip, ENDPOINT, if ok then "successo" else "fallimento")
              yield if ok then Right(Utente(v.id, v.email)) else Left(LoginError.CredenzialiNonValide)
            case _ =>
              Sicurezza.registraEvento(ip, ENDPOINT, "fallimento").as(Left(LoginError.CredenzialiNonValide))
        yield risultato
    }
