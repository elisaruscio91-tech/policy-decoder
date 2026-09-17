package decoder.backend.cli

import decoder.backend.config.AppConfig
import decoder.backend.db.Db
import decoder.backend.fonti.{Embeddings, FontiRepo}
import decoder.backend.http.HttpClient
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import zio.*

import java.io.File
import java.nio.file.{Files, Paths}

// Importa un documento (PDF o testo semplice) nell'archivio fonti usato per
// il RAG (Fonti.contestoPer, chiamato ad ogni analisi). Chunking a
// dimensione fissa (semplice split per caratteri con overlap, niente
// tokenizer/NLP) — scelta deliberatamente semplice: sufficiente per poche
// decine di documenti caricati manualmente da un admin, non ottimale per
// corpora grandi o frasi tagliate a metà.
//
// Uso: sbt "backend/runMain decoder.backend.cli.IngestaFonte <path-file.pdf|.txt> [titolo]"
//
// API PDFBox 3.x non compiler-verificata in questo ambiente: `Loader.loadPDF`
// è il metodo statico corretto per PDFBox 3 (in PDFBox 2 era
// `PDDocument.load`) — se `sbt compile` fallisce qui, è il primo posto da
// controllare.
object IngestaFonte extends ZIOAppDefault:

  private val DIMENSIONE_CHUNK = 1500
  private val SOVRAPPOSIZIONE  = 200

  private def estraiTesto(path: String): Task[String] = ZIO.attemptBlocking {
    if path.toLowerCase.endsWith(".pdf") then
      val doc = Loader.loadPDF(new File(path))
      try new PDFTextStripper().getText(doc)
      finally doc.close()
    else Files.readString(Paths.get(path))
  }

  private def spezza(testo: String): List[String] =
    if testo.length <= DIMENSIONE_CHUNK then List(testo)
    else
      val buf    = scala.collection.mutable.ListBuffer.empty[String]
      var inizio = 0
      while inizio < testo.length do
        val fine = math.min(inizio + DIMENSIONE_CHUNK, testo.length)
        buf += testo.substring(inizio, fine)
        inizio = if fine == testo.length then fine else fine - SOVRAPPOSIZIONE
      buf.toList

  private def esegui(args: List[String]): RIO[FontiRepo & AppConfig & HttpClient.Backend, Unit] =
    args match
      case path :: resto =>
        val titolo = resto.headOption.getOrElse(new File(path).getName)
        for
          cfg   <- ZIO.service[AppConfig]
          repo  <- ZIO.service[FontiRepo]
          testo <- estraiTesto(path)
          _     <- Console.printLine(s"Letto '$path' (${testo.length} caratteri), titolo: $titolo")
          chunks = spezza(testo)
          docId <- repo.inserisciDocumento(titolo, Some(path))
          _ <- ZIO.foreachDiscard(chunks.zipWithIndex) { case (chunk, i) =>
            for
              emb <- Embeddings.embedding(cfg.ai.ollama, cfg.embeddingModel, chunk)
              _   <- repo.inserisciChunk(docId, i, chunk, emb)
              _   <- Console.printLine(s"  chunk ${i + 1}/${chunks.length} indicizzato")
            yield ()
          }
          _ <- Console.printLine(s"OK: '$titolo' indicizzato in ${chunks.length} chunk.")
        yield ()
      case _ =>
        Console.printLine("Uso: <path-file.pdf|.txt> [titolo]") *> ZIO.fail(new IllegalArgumentException("argomenti mancanti"))

  override def run =
    for
      args <- getArgs
      _ <- esegui(args.toList).provide(AppConfig.layer, Db.dataSourceLayer, FontiRepo.layer, HttpClient.layer)
    yield ()
