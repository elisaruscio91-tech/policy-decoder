package decoder.backend.ia

import decoder.backend.config.{AiCfg, AnthropicCfg, OllamaCfg, OpenRouterCfg}
import decoder.backend.http.HttpClient
import io.circe.{Decoder, Json}
import io.circe.parser
import sttp.client3.*
import sttp.model.Uri
import zio.*

// Astrazione sul motore di analisi: Anthropic, OpenRouter, o Ollama
// self-hosted (privacy massima: il testo non lascia la tua VM). Cambiare
// motore = cambiare AI_PROVIDER in application.conf/env, zero modifiche al
// codice che chiama chiamaModello(). Porta 1:1 di src/lib/motore-ia.ts.
object MotoreIA:

  private val MAX_TOKENS = 8192
  // Il timeout di default del backend HTTP (60s) è troppo basso per
  // un'analisi con output fino a MAX_TOKENS: il modello può impiegare
  // più tempo di una chiamata API qualunque. Costruita esplicitamente
  // (non con `180.seconds`) per evitare l'estensione ambigua tra
  // scala.concurrent.duration e zio (entrambi importati con wildcard).
  private val TIMEOUT_MODELLO = scala.concurrent.duration.Duration(180, java.util.concurrent.TimeUnit.SECONDS)

  private def estraiMessaggioErrore(json: Json, fallback: String): String =
    json.hcursor.downField("error").downField("message").as[String].getOrElse(fallback)

  private def chiamaAnthropic(backend: HttpClient.Backend, cfg: AnthropicCfg, system: String, prompt: String): Task[String] =
    if cfg.apiKey.isEmpty then ZIO.fail(new RuntimeException("ANTHROPIC_API_KEY non configurata sul server"))
    else
      val headers = Map("x-api-key" -> cfg.apiKey, "anthropic-version" -> "2023-06-01") ++
        cfg.workspaceId.map(w => "anthropic-workspace-id" -> w).toList
      val body = Json.obj(
        "model"      -> Json.fromString(cfg.model),
        "max_tokens" -> Json.fromInt(MAX_TOKENS),
        "system"     -> Json.fromString(system),
        "messages"   -> Json.arr(Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(prompt))),
      )
      for
        resp <- basicRequest
          .post(uri"https://api.anthropic.com/v1/messages")
          .headers(headers)
          .contentType("application/json")
          .body(body.noSpaces)
          .response(asStringAlways)
          .readTimeout(TIMEOUT_MODELLO)
          .send(backend)
        json <- ZIO.fromEither(parser.parse(resp.body))
          .mapError(e => new RuntimeException(s"Risposta Anthropic non interpretabile: ${e.getMessage}"))
        _ <- ZIO
          .fail(new RuntimeException(estraiMessaggioErrore(json, s"Errore Anthropic ${resp.code.code}")))
          .unless(resp.code.isSuccess)
        testo = json.hcursor
          .downField("content")
          .as[List[Json]]
          .getOrElse(Nil)
          .flatMap(_.hcursor.downField("text").as[String].toOption)
          .mkString
      yield testo

  private def chiamaOpenRouter(backend: HttpClient.Backend, cfg: OpenRouterCfg, system: String, prompt: String): Task[String] =
    if cfg.apiKey.isEmpty then ZIO.fail(new RuntimeException("OPENROUTER_API_KEY non configurata sul server"))
    else
      val body = Json.obj(
        "model"      -> Json.fromString(cfg.model),
        "max_tokens" -> Json.fromInt(MAX_TOKENS),
        "messages" -> Json.arr(
          Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(system)),
          Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(prompt)),
        ),
      )
      for
        resp <- basicRequest
          .post(uri"https://openrouter.ai/api/v1/chat/completions")
          .header("Authorization", s"Bearer ${cfg.apiKey}")
          .contentType("application/json")
          .body(body.noSpaces)
          .response(asStringAlways)
          .readTimeout(TIMEOUT_MODELLO)
          .send(backend)
        json <- ZIO.fromEither(parser.parse(resp.body))
          .mapError(e => new RuntimeException(s"Risposta OpenRouter non interpretabile: ${e.getMessage}"))
        _ <- ZIO
          .fail(new RuntimeException(estraiMessaggioErrore(json, s"Errore OpenRouter ${resp.code.code}")))
          .unless(resp.code.isSuccess)
        testo = json.hcursor.downField("choices").downArray.downField("message").downField("content").as[String].getOrElse("")
      yield testo

  private def chiamaOllama(backend: HttpClient.Backend, cfg: OllamaCfg, system: String, prompt: String): Task[String] =
    if cfg.baseUrl.isEmpty then ZIO.fail(new RuntimeException("OLLAMA_BASE_URL non configurata sul server"))
    else
      val body = Json.obj(
        "model"      -> Json.fromString(cfg.model),
        "max_tokens" -> Json.fromInt(MAX_TOKENS),
        "messages" -> Json.arr(
          Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(system)),
          Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(prompt)),
        ),
      )
      val headers = cfg.apiKey.map(k => Map("Authorization" -> s"Bearer $k")).getOrElse(Map.empty[String, String])
      for
        uri <- ZIO
          .fromEither(Uri.parse(s"${cfg.baseUrl.stripSuffix("/")}/v1/chat/completions"))
          .mapError(e => new RuntimeException(s"OLLAMA_BASE_URL non valido: $e"))
        resp <- basicRequest
          .post(uri)
          .headers(headers)
          .contentType("application/json")
          .body(body.noSpaces)
          .response(asStringAlways)
          .readTimeout(TIMEOUT_MODELLO)
          .send(backend)
        json <- ZIO.fromEither(parser.parse(resp.body))
          .mapError(e => new RuntimeException(s"Risposta Ollama non interpretabile: ${e.getMessage}"))
        _ <- ZIO
          .fail(new RuntimeException(estraiMessaggioErrore(json, s"Errore Ollama ${resp.code.code}")))
          .unless(resp.code.isSuccess)
        testo = json.hcursor.downField("choices").downArray.downField("message").downField("content").as[String].getOrElse("")
      yield testo

  def chiamaModello(cfg: AiCfg, system: String, prompt: String): RIO[HttpClient.Backend, String] =
    ZIO.serviceWithZIO[HttpClient.Backend] { backend =>
      cfg.provider.toLowerCase match
        case "openrouter" => chiamaOpenRouter(backend, cfg.openrouter, system, prompt)
        case "ollama"     => chiamaOllama(backend, cfg.ollama, system, prompt)
        case _            => chiamaAnthropic(backend, cfg.anthropic, system, prompt)
    }

  /** Ripara un JSON troncato (può succedere se la risposta del modello supera
    * max_tokens): rimuove l'ultimo valore incompleto e richiude le parentesi
    * rimaste aperte.
    */
  private def riparaJsonTroncato(frammento: String): String =
    val senzaCoda = frammento.replaceAll(""",\s*[^{}\[\],"]*$""", "")
    var graffe = 0
    var quadre = 0
    for ch <- senzaCoda do
      ch match
        case '{' => graffe += 1
        case '}' => graffe -= 1
        case '[' => quadre += 1
        case ']' => quadre -= 1
        case _   => ()
    val sb = new StringBuilder(senzaCoda)
    while quadre > 0 do
      sb.append(']'); quadre -= 1
    while graffe > 0 do
      sb.append('}'); graffe -= 1
    sb.toString

  def estraiJson[T](raw: String)(using Decoder[T]): Task[T] =
    val cleaned = raw.replaceAll("""```json\s*""", "").replaceAll("""```\s*""", "").trim
    parser.decode[T](cleaned) match
      case Right(v) => ZIO.succeed(v)
      case Left(_) =>
        """(?s)\{.*\}""".r.findFirstIn(cleaned) match
          case None => ZIO.fail(new RuntimeException("Risposta del modello non interpretabile come JSON"))
          case Some(blocco) =>
            parser.decode[T](blocco) match
              case Right(v) => ZIO.succeed(v)
              case Left(_) =>
                parser.decode[T](riparaJsonTroncato(blocco)) match
                  case Right(v) => ZIO.succeed(v)
                  case Left(_) =>
                    ZIO.fail(
                      new RuntimeException("Risposta del modello troncata o non interpretabile. Prova con un testo più corto."),
                    )
