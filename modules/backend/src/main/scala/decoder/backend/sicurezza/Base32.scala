package decoder.backend.sicurezza

import org.apache.commons.codec.binary.{Base32 => CommonsBase32}

import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKey

// Il segreto TOTP è generato e salvato come stringa Base32 (RFC 4648) — stesso
// formato prodotto da otplib.generateSecret() nella versione Next.js, così i
// segreti già distribuiti agli utenti restano validi dopo la migrazione.
object Base32:
  private val decoder = new CommonsBase32()

  def decodeToKey(secretBase32: String, algorithm: String): SecretKey =
    new SecretKeySpec(decoder.decode(secretBase32.toUpperCase), algorithm)

  def generateSecret(): String = {
    val bytes = new Array[Byte](20) // 160 bit, stesso default di otplib
    new java.security.SecureRandom().nextBytes(bytes)
    decoder.encodeToString(bytes).replace("=", "")
  }
