package decoder.backend.articolo

import decoder.shared.Articolo
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import zio.*

// Estrae il testo da un PDF caricato dall'utente (tab "Documento"): stessa
// libreria (PDFBox) già usata da IngestaFonte.scala per il RAG e da
// PdfExport.scala per generare i PDF scaricabili — qui invece la leggiamo. A
// differenza di EstraiArticolo (URL) non parte nessuna richiesta HTTP: il
// file arriva già come bytes nel body della richiesta, quindi nessun rischio
// SSRF da gestire qui.
object EstraiPdf:

  final case class PdfTroppoGrandeException(maxMb: Int) extends Exception(s"File troppo grande (max ${maxMb}MB)")
  final case class PdfNonValidoException(msg: String) extends Exception(msg)

  // Limite sulla dimensione del file, non sul testo estratto (quello è
  // troncato più avanti da EndpointsImpl, vedi MAX_CARATTERI_DOCUMENTO): un
  // libro di solo testo in PDF pesa tipicamente qualche MB, questo limite
  // serve soprattutto a evitare di caricare in memoria scansioni enormi che
  // comunque PDFTextStripper non riuscirebbe a leggere (nessun OCR qui).
  private val MAX_BYTES = 10 * 1024 * 1024

  def estraiArticolo(bytes: Array[Byte], nomeFile: String): Task[Articolo] =
    for
      _ <- ZIO.when(bytes.isEmpty)(ZIO.fail(PdfNonValidoException("File PDF vuoto")))
      _ <- ZIO.when(bytes.length > MAX_BYTES)(ZIO.fail(PdfTroppoGrandeException(MAX_BYTES / (1024 * 1024))))
      coppia <- ZIO
        .attemptBlocking {
          val doc = Loader.loadPDF(bytes)
          try
            val testo  = new PDFTextStripper().getText(doc)
            val titolo = Option(doc.getDocumentInformation).flatMap(di => Option(di.getTitle)).map(_.trim).filter(_.nonEmpty)
            (titolo, testo)
          finally doc.close()
        }
        .mapError(e => PdfNonValidoException(s"Impossibile leggere il file: forse non è un PDF valido (${e.getMessage})"))
      (titoloMeta, testoGrezzo) = coppia
      testo                     = testoGrezzo.trim
      _ <- ZIO.when(testo.isEmpty)(
        ZIO.fail(PdfNonValidoException("Nessun testo estraibile dal PDF (potrebbe essere una scansione senza testo selezionabile)")),
      )
    yield Articolo(titolo = titoloMeta.getOrElse(nomeFile), testo = testo, fonte = nomeFile)
