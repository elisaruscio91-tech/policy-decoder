package decoder.backend.fonti

import decoder.backend.config.AppConfig
import decoder.backend.http.HttpClient
import zio.*

// Recupero (RAG) delle fonti caricate con IngestaFonte: chiamato da
// EndpointsImpl prima di ogni analisi. Funzionalità opt-in — se gli
// embeddings non sono configurati/raggiungibili (OLLAMA_BASE_URL vuota o
// irraggiungibile) o non ci sono chunk pertinenti, ritorna stringa vuota e
// l'analisi prosegue normalmente, senza RAG: non è mai un requisito per il
// funzionamento base dell'app.
object Fonti:

  private val TOP_K = 5

  // Cosine distance (pgvector `<->` con vector_cosine_ops): 0 = identico,
  // 2 = opposto. Soglia empirica, non tarata su dati reali (nessun ambiente
  // di test disponibile in fase di scrittura) — se il RAG in pratica risulta
  // troppo permissivo (fonti irrilevanti citate) o troppo restrittivo
  // (fonti pertinenti mai trovate), questo è il primo numero da toccare.
  private val DISTANZA_MASSIMA = 0.5

  def contestoPer(testo: String): RIO[FontiRepo & AppConfig & HttpClient.Backend, String] =
    for
      cfg   <- ZIO.service[AppConfig]
      repo  <- ZIO.service[FontiRepo]
      embed <- Embeddings
        .embedding(cfg.ai.ollama, cfg.embeddingModel, testo.take(4000))
        .catchAll(_ => ZIO.succeed(Vector.empty[Float]))
      trovati <- if embed.isEmpty then ZIO.succeed(Nil) else repo.cercaSimili(embed, TOP_K, DISTANZA_MASSIMA)
    yield
      if trovati.isEmpty then ""
      else
        "\n\n--- Fonti di riferimento fornite dall'utente (usale se pertinenti, citale esplicitamente se le usi) ---\n" +
          trovati.map(f => s"[${f.documentoTitolo}]\n${f.testo}").mkString("\n\n")
