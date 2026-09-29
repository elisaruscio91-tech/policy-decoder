package decoder.shared

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*

// DTO scambiati tra backend e frontend via Tapir/circe. Rispecchiano
// esattamente il contratto JSON già in uso dalla versione Next.js
// (vedi prompt-sistema.ts per lo schema di RisultatoAnalisi).

final case class LoginRequest(email: String, password: String, codice2fa: Option[String])
object LoginRequest:
  given Codec[LoginRequest] = deriveCodec

final case class Quota(limite: Int, usate: Int, rimanenti: Int)
object Quota:
  given Codec[Quota] = deriveCodec

// Niente cookie: la SPA conserva "token" (JWT) in sessionStorage e lo
// rimanda come header "Authorization: Bearer <token>" su ogni chiamata
// successiva (vedi README per il trade-off rispetto a un cookie httpOnly).
final case class LoginResponse(token: String, email: String, quota: Quota)
object LoginResponse:
  given Codec[LoginResponse] = deriveCodec

final case class AnalyzeRequest(
  testo: String,
  modalita: String, // "testo" | "url"
  outputLang: String, // "auto" | "en" | "it"
)
object AnalyzeRequest:
  given Codec[AnalyzeRequest] = deriveCodec

final case class FetchUrlRequest(url: String)
object FetchUrlRequest:
  given Codec[FetchUrlRequest] = deriveCodec

final case class Articolo(titolo: String, testo: String, fonte: String)
object Articolo:
  given Codec[Articolo] = deriveCodec

final case class Summary(text: Option[String], declared_intent: Option[String], actual_intent: Option[String])
object Summary:
  given Codec[Summary] = deriveCodec

final case class DangerScore(score: Option[Int], label: Option[String], explanation: Option[String])
object DangerScore:
  given Codec[DangerScore] = deriveCodec

final case class Rhetoric(
  framework: Option[String],
  technique: Option[String],
  quote: Option[String],
  analysis: Option[String],
  severity: Option[Int],
)
object Rhetoric:
  given Codec[Rhetoric] = deriveCodec

final case class FactCheck(
  claim: Option[String],
  evidence: Option[String],
  source: Option[String],
  verdict: Option[String],
  verdict_explain: Option[String],
)
object FactCheck:
  given Codec[FactCheck] = deriveCodec

final case class Legal(heading: Option[String], text: Option[String])
object Legal:
  given Codec[Legal] = deriveCodec

final case class CounterNarrative(title: Option[String], text: Option[String], source: Option[String])
object CounterNarrative:
  given Codec[CounterNarrative] = deriveCodec

final case class Slide(emoji: Option[String], title: Option[String], body: Option[String])
object Slide:
  given Codec[Slide] = deriveCodec

final case class RisultatoAnalisi(
  summary: Option[Summary],
  danger_score: Option[DangerScore],
  rhetoric: Option[List[Rhetoric]],
  factcheck: Option[List[FactCheck]],
  legal: Option[List[Legal]],
  counter_narrative: Option[List[CounterNarrative]],
  slides: Option[List[Slide]],
)
object RisultatoAnalisi:
  given Codec[RisultatoAnalisi] = deriveCodec

final case class AnalyzeResponse(risultato: RisultatoAnalisi, quota: Quota)
object AnalyzeResponse:
  given Codec[AnalyzeResponse] = deriveCodec

final case class ErroreApi(error: String, quota: Option[Quota] = None)
object ErroreApi:
  given Codec[ErroreApi] = deriveCodec

// Richiesta di export PDF: il client rimanda il risultato che ha già in
// memoria (nessuna nuova chiamata all'IA) insieme alla lingua dell'interfaccia
// (per le etichette statiche del PDF, es. "Sintesi" vs "Summary") e allo scopo
// ("tutto" = analisi completa, "slide" = solo la sezione slide social).
final case class ExportPdfRequest(risultato: RisultatoAnalisi, uiLang: String, scope: String)
object ExportPdfRequest:
  given Codec[ExportPdfRequest] = deriveCodec
