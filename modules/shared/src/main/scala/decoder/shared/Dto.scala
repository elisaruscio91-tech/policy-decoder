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

// Sezione "Conformità normativa italiana": solo per testi di stampa/dichiarazioni
// in lingua italiana (mai per contenuti in altre lingue o non giornalistici —
// vedi istruzione nel prompt). Gli articoli citati provengono da un elenco
// chiuso e verificato incorporato nel prompt stesso (ODG, AGCOM, IAP, Codice
// del Consumo/Codacons): l'IA non deve mai inventare un numero di articolo.
final case class ArticoloViolato(numero: Option[String], titolo: Option[String], violazione: Option[String])
object ArticoloViolato:
  given Codec[ArticoloViolato] = deriveCodec

final case class NormativaItaliana(
  ente: Option[String], // "Ordine dei Giornalisti" | "AGCOM" | "IAP" | "Codacons / Codice del Consumo"
  applicabile: Option[Boolean],
  motivazione_non_applicabile: Option[String],
  articoli: Option[List[ArticoloViolato]],
)
object NormativaItaliana:
  given Codec[NormativaItaliana] = deriveCodec

final case class RisultatoAnalisi(
  summary: Option[Summary],
  danger_score: Option[DangerScore],
  rhetoric: Option[List[Rhetoric]],
  factcheck: Option[List[FactCheck]],
  legal: Option[List[Legal]],
  counter_narrative: Option[List[CounterNarrative]],
  slides: Option[List[Slide]],
  normativa_italiana: Option[List[NormativaItaliana]],
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

// Form "Registrati": l'utente sceglie email+password ma l'account resta in
// attesa di approvazione manuale (vedi EndpointsImpl.richiediAccessoLogic e
// decoder.backend.cli.GestisciRichieste) — niente login immediato.
final case class RichiediAccessoRequest(email: String, password: String)
object RichiediAccessoRequest:
  given Codec[RichiediAccessoRequest] = deriveCodec

final case class MessaggioOk(messaggio: String)
object MessaggioOk:
  given Codec[MessaggioOk] = deriveCodec

// Marker usati al posto di un messaggio d'errore grezzo quando il frontend
// deve mostrare un testo bilingue tradotto localmente (via I18n) invece del
// messaggio del backend, che è solo in italiano.
object CodiciErrore:
  val SitoBloccatoLettura = "SITO_BLOCCATO_LETTURA_AUTOMATICA"
