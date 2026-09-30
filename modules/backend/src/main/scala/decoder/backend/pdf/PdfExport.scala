package decoder.backend.pdf

import decoder.shared.RisultatoAnalisi
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.{PDFont, PDType1Font, Standard14Fonts}

import java.awt.Color
import java.io.ByteArrayOutputStream

// Genera il PDF scaricabile dal frontend (bottone "Scarica analisi (PDF)" /
// "Scarica testo per slide (PDF)") a partire da un RisultatoAnalisi che il
// client ha già in memoria: nessuna nuova chiamata all'IA qui, solo
// formattazione con PDFBox (già dipendenza del backend, usata altrove per
// leggere PDF in ingesta — qui invece li scriviamo).
//
// Stesso tema scuro/viola del sito (vedi style.css): sfondo scuro per
// pagina, titoli in Times-Bold (il font serif più vicino tra i 14 font
// standard di PDFBox a "Playfair Display", che richiederebbe di
// incorporare un file .ttf non presente nel repo), corpo in Helvetica
// (sans, come "DM Sans"). Colori ripresi 1:1 dalle stesse soglie usate in
// AnalyzeView.scala (rischio/gravità/verdetto fact-check) e dalle
// variabili CSS in style.css (:root).
object PdfExport:

  private val MARGINE          = 50f
  private val LARGHEZZA_PAGINA = PDRectangle.A4.getWidth
  private val ALTEZZA_PAGINA   = PDRectangle.A4.getHeight
  private val LARGHEZZA_TESTO  = LARGHEZZA_PAGINA - 2 * MARGINE

  private val fontTitolo  = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)
  private val fontSezione = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)
  private val fontTesto   = new PDType1Font(Standard14Fonts.FontName.HELVETICA)
  private val fontCorsivo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)

  private def esadecimale(hex: String): Color = Color.decode(hex)

  private val COL_SFONDO       = esadecimale("#060610")
  private val COL_TITOLO       = esadecimale("#f1f5f9")
  private val COL_TESTO        = esadecimale("#b8c0d8")
  private val COL_TESTO_MUTO   = esadecimale("#7b8ba8")
  private val COL_TESTO_DEBOLE = esadecimale("#3b4768")
  private val COL_VIOLA_CHIARO = esadecimale("#c39bd3")
  private val COL_QUOTE        = esadecimale("#5b6880")
  private val COL_AZZURRO      = esadecimale("#7dd3fc")
  private val COL_VERDE        = esadecimale("#16a34a")

  // Stesse soglie di AnalyzeView.scala (coloreRischio/coloreGravita/coloreVerdetto).
  private def coloreRischio(score: Int): Color =
    if score >= 80 then esadecimale("#dc2626")
    else if score >= 60 then esadecimale("#ea580c")
    else if score >= 40 then esadecimale("#d97706")
    else if score >= 20 then esadecimale("#2563eb")
    else esadecimale("#16a34a")

  private def coloreGravita(sev: Int): Color =
    if sev >= 8 then esadecimale("#dc2626") else if sev >= 5 then esadecimale("#d97706") else esadecimale("#2563eb")

  private def coloreVerdetto(verdetto: String): Color =
    val v = verdetto.toLowerCase
    if v.contains("false") || v.contains("falso") then esadecimale("#dc2626")
    else if v.contains("misleading") || v.contains("fuorviante") then esadecimale("#ea580c")
    else if v.contains("partially") || v.contains("parzialmente") then esadecimale("#d97706")
    else if v.contains("unverifiable") || v.contains("non verificabile") then esadecimale("#9333ea")
    else if v == "true" || v == "vero" then esadecimale("#16a34a")
    else esadecimale("#64748b")

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
    private var versionePagina: Int         = 0

    def currentY: Float   = y
    def numeroPagina: Int = versionePagina

    private def nuovaPagina(): PDPage =
      val p = new PDPage(PDRectangle.A4)
      doc.addPage(p)
      p

    // Sfondo scuro su ogni pagina, come var(--bg) in style.css.
    private def nuovoStream(p: PDPage): PDPageContentStream =
      val cs = new PDPageContentStream(doc, p)
      cs.setNonStrokingColor(COL_SFONDO)
      cs.addRect(0f, 0f, LARGHEZZA_PAGINA, ALTEZZA_PAGINA)
      cs.fill()
      cs

    private def saltaPagina(): Unit =
      stream.close()
      pagina = nuovaPagina()
      stream = nuovoStream(pagina)
      y = ALTEZZA_PAGINA - MARGINE
      versionePagina += 1

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
      colore: Color = COL_TESTO,
    ): Unit =
      val testo = pulisci(testoGrezzo)
      if testo.trim.nonEmpty then
        y -= spazioSopra
        for riga <- spezza(testo.trim, font, dimensione) do
          assicuraSpazio(interlinea)
          stream.beginText()
          stream.setNonStrokingColor(colore)
          stream.setFont(font, dimensione)
          stream.newLineAtOffset(MARGINE, y)
          stream.showText(riga)
          stream.endText()
          y -= interlinea

    def scriviTitolo(testo: String): Unit =
      scriviParagrafo(testo, fontTitolo, 20f, 24f, 0f, COL_TITOLO)
      y -= 10f

    def scriviIntestazioneSezione(testo: String): Unit =
      assicuraSpazio(30f)
      y -= 14f
      scriviParagrafo(testo, fontSezione, 14f, 18f, 0f, COL_VIOLA_CHIARO)

    // Bordo colorato a sinistra di un blocco di paragrafi — stesso motivo
    // "border-left" usato per le card in style.css (.retorica-item,
    // .contronarrazione-item, ecc.).
    def bordoBlocco(yTop: Float, yBottom: Float, colore: Color): Unit =
      stream.setStrokingColor(colore)
      stream.setLineWidth(2.5f)
      stream.moveTo(MARGINE - 10f, yTop)
      stream.lineTo(MARGINE - 10f, yBottom)
      stream.stroke()

    def chiudi(): Unit = stream.close()

  /** scope: "tutto" = tutte le sezioni, "slide" = solo il testo suggerito per slide social. */
  def genera(r: RisultatoAnalisi, uiLang: String, scope: String): Array[Byte] =
    val it = uiLang != "en"
    def t(itS: String, enS: String): String = if it then itS else enS

    val doc = new PDDocument()
    val w   = new Scrittore(doc)

    // Scrive un blocco (una tecnica retorica, un fact-check, ecc.) e ne
    // disegna il bordo colorato a sinistra solo se non è stato interrotto da
    // un cambio pagina nel frattempo (altrimenti yTop/yBottom si
    // riferirebbero a pagine diverse).
    def blocco(colore: Color)(corpo: => Unit): Unit =
      val paginaPrima = w.numeroPagina
      val yTop        = w.currentY
      corpo
      val yBottom = w.currentY
      if w.numeroPagina == paginaPrima then w.bordoBlocco(yTop, yBottom, colore)

    try
      w.scriviTitolo(t("Policy Decoder - Analisi", "Policy Decoder - Analysis"))

      if scope != "slide" then
        r.summary.foreach { s =>
          w.scriviIntestazioneSezione(t("Sintesi", "Summary"))
          s.text.foreach(v => w.scriviParagrafo(v))
          s.declared_intent.foreach(v =>
            w.scriviParagrafo(s"${t("Intento dichiarato", "Declared intent")}: $v", colore = COL_TESTO_MUTO),
          )
          s.actual_intent.foreach(v =>
            w.scriviParagrafo(s"${t("Intento reale", "Actual intent")}: $v", colore = COL_VIOLA_CHIARO),
          )
        }
        r.danger_score.foreach { ds =>
          w.scriviIntestazioneSezione(t("Livello di rischio", "Risk level"))
          val colore       = ds.score.map(coloreRischio).getOrElse(COL_TESTO)
          val punteggio    = ds.score.map(v => s"$v/100").getOrElse("")
          val intestazione = List(ds.label.getOrElse(""), punteggio).filter(_.nonEmpty).mkString(" - ")
          if intestazione.nonEmpty then w.scriviParagrafo(intestazione, fontSezione, 16f, colore = colore)
          ds.explanation.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
        }
        r.rhetoric.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Tecniche retoriche", "Rhetoric techniques"))
          items.foreach { el =>
            val colore = el.severity.map(coloreGravita).getOrElse(COL_TESTO_DEBOLE)
            blocco(colore) {
              val intestazione = List(el.framework, el.technique).flatten.mkString(" - ")
              if intestazione.nonEmpty then w.scriviParagrafo(intestazione, fontSezione, 12f, colore = COL_TITOLO)
              el.quote.foreach(v => w.scriviParagrafo("\u00ab" + v + "\u00bb", fontCorsivo, colore = COL_QUOTE))
              el.analysis.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
              el.severity.foreach(v => w.scriviParagrafo(s"${t("Gravita", "Severity")}: $v/10", colore = colore))
            }
          }
        }
        r.factcheck.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Fact-check", "Fact-check"))
          items.foreach { el =>
            val colore = el.verdict.map(coloreVerdetto).getOrElse(COL_TESTO_DEBOLE)
            blocco(colore) {
              val intestazione = List(el.verdict, el.claim.map(c => "\u00ab" + c + "\u00bb")).flatten.mkString(" - ")
              if intestazione.nonEmpty then w.scriviParagrafo(intestazione, fontSezione, 12f, colore = colore)
              el.evidence.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
              el.source.foreach(v =>
                w.scriviParagrafo(s"${t("Fonte", "Source")}: $v", fontCorsivo, 10f, colore = COL_TESTO_DEBOLE),
              )
              el.verdict_explain.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
            }
          }
        }
        r.legal.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Quadro giuridico", "Legal framework"))
          items.foreach { el =>
            blocco(COL_AZZURRO) {
              el.heading.foreach(v => w.scriviParagrafo(v, fontSezione, 12f, colore = COL_AZZURRO))
              el.text.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
            }
          }
        }
        r.counter_narrative.filter(_.nonEmpty).foreach { items =>
          w.scriviIntestazioneSezione(t("Contro-narrazione", "Counter-narrative"))
          items.foreach { el =>
            blocco(COL_VERDE) {
              el.title.foreach(v => w.scriviParagrafo(v, fontSezione, 12f, colore = COL_TITOLO))
              el.text.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
              el.source.foreach(v =>
                w.scriviParagrafo(s"${t("Fonte", "Source")}: $v", fontCorsivo, 10f, colore = COL_TESTO_DEBOLE),
              )
            }
          }
        }

      r.slides.filter(_.nonEmpty).foreach { items =>
        w.scriviIntestazioneSezione(t("Testo suggerito per slide social", "Suggested text for social slides"))
        w.scriviParagrafo(
          t(
            "Testo suggerito, da adattare graficamente per le tue slide social.",
            "Suggested text - adapt it visually for your own social slides.",
          ),
          fontCorsivo,
          10f,
          colore = COL_TESTO_DEBOLE,
        )
        items.foreach { sl =>
          blocco(COL_VIOLA_CHIARO) {
            sl.title.foreach(v => w.scriviParagrafo(v, fontSezione, 12f, colore = COL_TITOLO))
            sl.body.foreach(v => w.scriviParagrafo(v, colore = COL_TESTO_MUTO))
          }
        }
      }
    finally w.chiudi()

    val out = new ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    out.toByteArray
