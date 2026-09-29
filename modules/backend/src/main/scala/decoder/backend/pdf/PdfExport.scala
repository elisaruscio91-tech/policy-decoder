package decoder.backend.pdf

import decoder.shared.RisultatoAnalisi
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.{PDFont, PDType1Font, Standard14Fonts}

import java.io.ByteArrayOutputStream

// Genera il PDF scaricabile dal frontend (bottone "Scarica analisi (PDF)" /
// "Scarica testo per slide (PDF)") a partire da un RisultatoAnalisi che il
// client ha già in memoria: nessuna nuova chiamata all'IA qui, solo
// formattazione testuale con PDFBox (già dipendenza del backend, usata
// altrove per leggere PDF in ingesta — qui invece li scriviamo).
object PdfExport:

  private val MARGINE          = 50f
  private val LARGHEZZA_PAGINA = PDRectangle.A4.getWidth
  private val ALTEZZA_PAGINA   = PDRectangle.A4.getHeight
  private val LARGHEZZA_TESTO  = LARGHEZZA_PAGINA - 2 * MARGINE

  private val fontTitolo  = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
  private val fontSezione = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
  private val fontTesto   = new PDType1Font(Standard14Fonts.FontName.HELVETICA)

  // I font Standard14 di PDFBox usano WinAnsiEncoding (~Latin-1): niente
  // emoji, niente Unicode oltre U+00FF. Normalizziamo prima la punteggiatura
  // tipografica più comune (altrimenti resa come '?' o rifiutata) e poi
  // scartiamo qualunque carattere fuori da quel range (es. le emoji in
  // Slide.emoji) invece di lasciare che showText() lanci un'eccezione.
  private def pulisci(s: String): String =
    s.replace('\u2018', '\'').replace('\u2019', '\'')
      .replace('\u201c', '"').replace('\u201d', '"')
      .replace('\u2013', '-').replace('\u2014', '-')
      .replace("\u2026", "...")
      .filter(_ <= '\u00ff')

  private class Scrittore(doc: PDDocument):
    private var pagina: PDPage             = nuovaPagina()
    private var stream: PDPageContentStream = nuovoStream(pagina)
    private var y: Float                    = ALTEZZA_PAGINA - MARGINE

    private def nuovaPagina(): PDPage =
      val p = new PDPage(PDRectangle.A4)
      doc.addPage(p)
      p

    private def nuovoStream(p: PDPage): PDPageContentStream =
      new PDPageContentStream(doc, p)

    private def saltaPagina(): Unit =
      stream.close()
      pagina = nuovaPagina()
      stream = nuovoStream(pagina)
      y = ALTEZZA_PAGINA - MARGINE

    private def assicuraSpazio(altezzaRiga: Float): Unit =
      if y - altezzaRiga < MARGINE then saltaPagina()

    // Suddivide il testo in righe che rientrano in LARGHEZZA_TESTO per il
    // font/dimensione dati: PDFBox non fa wrapping automatico.
    private def spezza(testo: String, font: PDFont, dimensione: Float): List[String] =
      val parole = testo.split("\\s+").filter(_.nonEmpty)
      val righe  = scala.collection.mutable.ListBuffer.empty[String]
      var corrente = new StringBuilder
      for parola <- parole do
        val prova = if corrente.isEmpty then parola else s"$corrente $parola"
        if font.getStringWidth(prova) / 1000f * dimensione <= LARGHEZZA_TESTO then
          corrente = new StringBuilder(prova)
        else
          if corrente.nonEmpty then righe += corrente.toString
          corrente = new StringBuilder(parola)
      if corrente.nonEmpty then righe += corrente.toString
      if righe.isEmpty then List("") else righe.toList

    def scriviParagrafo(
      testoGrezzo: String,
      font: PDFont = fontTesto,
      dimensione: Float = 11f,
      interlinea: Float = 15f,
      spazioSopra: Float = 6f,
    ): Unit =
      val testo = pulisci(testoGrezzo)
      if testo.trim.nonEmpty then
        y -= spazioSopra
        for riga <- spezza(testo.trim, font, dimensione) do
          assicuraSpazio(interlinea)
          stream.beginText()
          stream.setFont(font, dimensione)
          stream.newLineAtOffset(MARGINE, y)
          stream.showText(riga)
          stream.endText()
          y -= interlinea

    def scriviTitolo(testo: String): Unit =
      scriviParagrafo(testo, fontTitolo, 20f, 24f, 0f)
      y -= 10f

    def scriviIntestazioneSezione(testo: String): Unit =
      assicuraSpazio(30f)
      y -= 14f
      scriviParagrafo(testo, fontSezione, 14f, 18f, 0f)

    def chiudi(): Unit = stream.close()

  /** scope: "tutto" = tutte le sezioni, "slide" = solo il testo suggerito per slide social. */
  def genera(r: RisultatoAnalisi, uiLang: String, scope: String): Array[Byte] =
    val it = uiLang != "en"
    def t(itS: String, enS: String): String = if it then itS else enS

    val doc = new PDDocument()
    val w   = new Scrittore(doc)
    try
      w.scriviTitolo(t("Policy Decoder - Analisi", "Policy Decoder - Analysis"))

      if scope != "slide" then
        r.summary.foreach { s =>
          w.scriviIntestazioneSezione(t("Sintesi", "Summary"))
          s.text.foreach(v => w.scriviParagrafo(v))
          s.declared_intent.foreach(v => w.scriviParagrafo(s"${t("Intento dichiarato", "Declared intent")}: $v"))
          s.actual_intent.foreach(v => w.scriviParagrafo(s"${t("Intento reale", "Actual intent")}: $v"))
        }
        r.danger_score.foreach { ds =>
          w.scriviIntestazioneSezione(t("Livello di rischio", "Risk level"))
          val punteggio = ds.score.map(v => s"$v/100").getOrElse("")
          val intestazione = List(ds.label.getOrElse(""), punteggio).filter(_.nonEmpty).mkString(" - ")
          if intestazione.nonEmpty then w.scriviParagrafo(intestazione, fontSezione, 12f)
          ds.explanation.foreach(v => w.scriviParagrafo(v))
        }
        r.rhetoric.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Tecniche retoriche", "Rhetoric techniques"))
          items.foreach { el =>
            val intestazione = List(el.framework, el.technique).flatten.mkString(" - ")
            if intestazione.nonEmpty then w.scriviParagrafo(intestazione, fontSezione, 12f)
            el.quote.foreach(v => w.scriviParagrafo("\u00ab" + v + "\u00bb"))
            el.analysis.foreach(v => w.scriviParagrafo(v))
            el.severity.foreach(v => w.scriviParagrafo(s"${t("Gravita", "Severity")}: $v/10"))
          }
        }
        r.factcheck.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Fact-check", "Fact-check"))
          items.foreach { el =>
            val intestazione = List(el.verdict, el.claim.map(c => "\u00ab" + c + "\u00bb")).flatten.mkString(" - ")
            if intestazione.nonEmpty then w.scriviParagrafo(intestazione, fontSezione, 12f)
            el.evidence.foreach(v => w.scriviParagrafo(v))
            el.source.foreach(v => w.scriviParagrafo(s"${t("Fonte", "Source")}: $v"))
            el.verdict_explain.foreach(v => w.scriviParagrafo(v))
          }
        }
        r.legal.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Quadro giuridico", "Legal framework"))
          items.foreach { el =>
            el.heading.foreach(v => w.scriviParagrafo(v, fontSezione, 12f))
            el.text.foreach(v => w.scriviParagrafo(v))
          }
        }
        r.counter_narrative.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Contro-narrazione", "Counter-narrative"))
          items.foreach { el =>
            el.title.foreach(v => w.scriviParagrafo(v, fontSezione, 12f))
            el.text.foreach(v => w.scriviParagrafo(v))
            el.source.foreach(v => w.scriviParagrafo(s"${t("Fonte", "Source")}: $v"))
          }
        }

      r.slides.filter(_.nonEmpty).foreach { items =>
        w.scriviIntestazioneSezione(t("Testo suggerito per slide social", "Suggested text for social slides"))
        w.scriviParagrafo(
          t(
            "Testo suggerito, da adattare graficamente per le tue slide social.",
            "Suggested text - adapt it visually for your own social slides.",
          ),
          fontTesto,
          10f,
        )
        items.foreach { sl =>
          sl.title.foreach(v => w.scriviParagrafo(v, fontSezione, 12f))
          sl.body.foreach(v => w.scriviParagrafo(v))
        }
      }
    finally w.chiudi()

    val out = new ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    out.toByteArray
