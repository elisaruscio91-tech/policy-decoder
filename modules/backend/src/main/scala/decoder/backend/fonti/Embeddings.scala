package decoder.backend.fonti

import decoder.backend.config.OllamaCfg
import decoder.backend.http.HttpClient
import io.circe.Json
import io.circe.parser
import sttp.client3.*
import sttp.model.Uri
import zio.*

// Chiama l'endpoint OpenAI-compatible `/v1/embeddings` esposto da Ollama (o
// da un gateway self-hosted davanti a Ollama, es. OmniRoute) per trasformare
// testo in un vettore numerico. Riusa deliberatamente OllamaCfg (baseUrl +
// apiKey) invece di introdurre una configurazione parallela: gli embeddings
// delle proprie fonti private devono restare sempre sull'infrastruttura
// self-hosted, indipendentemente da quale provider (anche esterno, es.
// Anthropic) sia scelto per l'analisi vera e propria via AI_PROVIDER.
object Embeddings:

  def embedding(cfg: OllamaCfg, modello: String, testo: String): RIO[HttpClient.Backend, Vector[Float]] =
    if cfg.baseUrl.isEmpty then
      ZIO.fail(new RuntimeException("OLLAMA_BASE_URL non configurata: necessaria per gli embeddings delle fonti (RAG)"))
    else
      ZIO.serviceWithZIO[HttpClient.Backend] { backend =>
        val body = Json.obj(
          "model" -> Json.fromString(modello),
          "input" -> Json.fromString(testo),
        )
        val headers = cfg.apiKey.map(k => Map("Authorization" -> s"Bearer $k")).getOrElse(Map.empty[String, String])
        for
          uri <- ZIO
            .fromEither(Uri.parse(s"${cfg.baseUrl.stripSuffix("/")}/v1/embeddings"))
            .mapError(e => new RuntimeException(s"OLLAMA_BASE_URL non valido: $e"))
          resp <- basicRequest
            .post(uri)
            .headers(headers)
            .contentType("application/json")
            .body(body.noSpaces)
            .response(asStringAlways)
            .send(backend)
          json <- ZIO
            .fromEither(parser.parse(resp.body))
            .mapError(e => new RuntimeException(s"Risposta embeddings non interpretabile: ${e.getMessage}"))
          _ <- ZIO
            .fail(new RuntimeException(s"Errore embeddings ${resp.code.code}: ${resp.body.take(300)}"))
            .unless(resp.code.isSuccess)
          vettore = json.hcursor.downField("data").downArray.downField("embedding").as[Vector[Float]].getOrElse(Vector.empty)
          _ <- ZIO.fail(new RuntimeException("Embedding vuoto nella risposta")).when(vettore.isEmpty)
        yield vettore
      }
