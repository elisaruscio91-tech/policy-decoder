package decoder.frontend

import com.raquo.laminar.api.L.*
import decoder.shared.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

// Vista principale: tab testo/url, selettore lingua di output, invio
// analisi, rendering delle 7 sezioni del risultato, download PDF. Estetica
// ripresa dalla vecchia versione React (tema scuro, accento viola ACET) —
// vedi style.css. L'interfaccia (etichette/pulsanti) è switchabile IT/EN via
// I18n; l'"outputLang" qui sotto resta un concetto separato: la lingua in cui
// l'IA scrive il testo dell'analisi.
object AnalyzeView:

  private val MAX_CARATTERI = 20_000
  private val EMAIL_ASSISTENZA = "elisa.ruscio@proton.me"

  private def msgErroreGenerico(): String =
    I18n.now(
      s"Si è verificato un errore imprevisto. Riprova tra qualche minuto; se il problema persiste scrivi a $EMAIL_ASSISTENZA.",
      s"An unexpected error occurred. Please try again in a few minutes; if the problem persists, contact $EMAIL_ASSISTENZA.",
    )

  // Difesa in profondità: anche se il backend è già stato sistemato per non
  // restituire mai messaggi grezzi, un proxy/CDN davanti al server (es.
  // Cloudflare) può restituire le sue pagine di errore HTML prima ancora che
  // la richiesta arrivi al backend — non vanno mai mostrate così come sono.
  private def messaggioSicuro(raw: String): String =
    val pulito = Option(raw).getOrElse("")
    if pulito.isEmpty || pulito.length > 300 || pulito.contains("<") then msgErroreGenerico() else pulito

  // Palette di colori per punteggio di rischio / gravità / verdetto,
  // ripresa 1:1 dalla vecchia versione React.
  private def coloreRischio(score: Int): String =
    if score >= 80 then "#dc2626"
    else if score >= 60 then "#ea580c"
    else if score >= 40 then "#d97706"
    else if score >= 20 then "#2563eb"
    else "#16a34a"

  private def coloreGravita(sev: Int): String =
    if sev >= 8 then "#dc2626" else if sev >= 5 then "#d97706" else "#2563eb"

  private def coloreVerdetto(verdetto: String): String =
    val v = verdetto.toLowerCase
    if v.contains("false") || v.contains("falso") then "#dc2626"
    else if v.contains("misleading") || v.contains("fuorviante") then "#ea580c"
    else if v.contains("partially") || v.contains("parzialmente") then "#d97706"
    else if v.contains("unverifiable") || v.contains("non verificabile") then "#9333ea"
    else if v == "true" || v == "vero" then "#16a34a"
    else "#64748b"

  def render(
    token: String,
    emailSignal: Signal[Option[String]],
    quotaVar: Var[Option[Quota]],
    logout: () => Unit,
    aggiornaQuota: String => Unit,
  ): HtmlElement =
    val modeVar      = Var("testo") // "testo" | "url"
    val testoVar     = Var("")
    val urlVar       = Var("")
    val linguaVar    = Var("auto") // "auto" | "en" | "it"
    val risultatoVar = Var(Option.empty[RisultatoAnalisi])
    val erroreVar    = Var(Option.empty[String])
    val inCorsoVar   = Var(false)
    val pdfInCorsoVar = Var(false)

    def recuperaTesto(): Future[Either[String, (String, String)]] =
      if modeVar.now() == "url" then
        Api.fetchUrl(token, FetchUrlRequest(urlVar.now().trim)).map {
          case Right(art)     => Right((art.testo, "url"))
          case Left((_, err)) => Left(err.error)
        }
      else Future.successful(Right((testoVar.now().trim, "testo")))

    def submit(): Unit =
      erroreVar.set(None)
      risultatoVar.set(None)
      inCorsoVar.set(true)
      val lingua = linguaVar.now()
      recuperaTesto()
        .flatMap {
          case Left(msg) => Future.successful(Left(msg))
          case Right((testo, modalita)) =>
            Api.analyze(token, AnalyzeRequest(testo, modalita, lingua)).map {
              case Right(resp) =>
                quotaVar.set(Some(resp.quota))
                Right(resp.risultato)
              case Left((_, err)) =>
                err.quota.foreach(q => quotaVar.set(Some(q)))
                Left(err.error)
            }
        }
        .onComplete {
          case Success(Right(ris)) =>
            inCorsoVar.set(false)
            risultatoVar.set(Some(ris))
          case Success(Left(msg)) =>
            inCorsoVar.set(false)
            erroreVar.set(Some(messaggioSicuro(msg)))
          case Failure(_) =>
            // Errori di rete/decodifica (es. il server non ha risposto affatto,
            // o un proxy davanti ad esso ha restituito una sua pagina di
            // errore): nessun dettaglio tecnico è utile all'utente qui.
            inCorsoVar.set(false)
            erroreVar.set(Some(msgErroreGenerico()))
        }

    def scaricaPdf(scopo: String): Unit =
      risultatoVar.now().foreach { r =>
        pdfInCorsoVar.set(true)
        Api.exportPdf(token, ExportPdfRequest(r, I18n.codice(), scopo)).onComplete {
          case Success(Right(bytes)) =>
            pdfInCorsoVar.set(false)
            val nomeFile = if scopo == "slide" then "policy-decoder-slide-social.pdf" else "policy-decoder-analisi.pdf"
            Download.salvaPdf(bytes, nomeFile)
          case Success(Left((_, err))) =>
            pdfInCorsoVar.set(false)
            erroreVar.set(Some(messaggioSicuro(err.error)))
          case Failure(_) =>
            pdfInCorsoVar.set(false)
            erroreVar.set(Some(msgErroreGenerico()))
        }
      }

    def sezione(titolo: Signal[String], contenuto: Modifier[HtmlElement]*): HtmlElement =
      div(cls := "sezione", h2(child.text <-- titolo), div(contenuto*))

    def renderRisultato(r: RisultatoAnalisi): HtmlElement =
      div(
        cls := "risultato",
        r.summary.map(s =>
          sezione(
            I18n.tr("Sintesi", "Summary"),
            div(
              cls := "card",
              p(s.text.getOrElse("")),
              div(
                cls := "riquadri-intento",
                div(
                  cls := "riquadro-intento",
                  div(cls := "etichetta", child.text <-- I18n.tr("Intento dichiarato", "Declared intent")),
                  p(s.declared_intent.getOrElse("")),
                ),
                div(
                  cls := "riquadro-intento reale",
                  div(cls := "etichetta", child.text <-- I18n.tr("Intento reale", "Actual intent")),
                  p(s.actual_intent.getOrElse("")),
                ),
              ),
            ),
          ),
        ),
        r.danger_score.map { ds =>
          val score   = ds.score.getOrElse(0)
          val colore  = coloreRischio(score)
          val stileAnello =
            s"--percentuale: $score; --colore-rischio: $colore;"
          sezione(
            I18n.tr("Livello di rischio", "Risk level"),
            div(
              cls := "rischio-card",
              div(
                cls := "anello-rischio",
                styleAttr := stileAnello,
                div(
                  cls := "anello-rischio-valore",
                  span(cls := "numero", styleAttr := s"color: $colore", score.toString),
                  span(cls := "su-cento", "/100"),
                ),
              ),
              span(cls := "rischio-etichetta", styleAttr := s"color: $colore", ds.label.getOrElse("")),
              p(ds.explanation.getOrElse("")),
            ),
          )
        },
        r.rhetoric.filter(_.nonEmpty).map(items =>
          sezione(
            I18n.tr("Tecniche retoriche", "Rhetoric techniques"),
            ul(
              items.map { it =>
                val sev    = it.severity.getOrElse(0)
                val colore = coloreGravita(sev)
                li(
                  cls := "retorica-item",
                  styleAttr := s"--colore-gravita: $colore",
                  div(cls := "retorica-framework", it.framework.getOrElse("")),
                  h3(it.technique.getOrElse("")),
                  p(cls := "quote", "\u00ab" + it.quote.getOrElse("") + "\u00bb"),
                  p(it.analysis.getOrElse("")),
                  div(
                    cls := "barra-gravita",
                    span(cls := "etichetta", child.text <-- I18n.tr("Gravità", "Severity")),
                    div(cls := "traccia", div(cls := "riempimento", styleAttr := s"--gravita: $sev; --colore-gravita: $colore")),
                    span(cls := "valore", styleAttr := s"color: $colore", s"$sev/10"),
                  ),
                )
              },
            ),
          ),
        ),
        r.factcheck.filter(_.nonEmpty).map(items =>
          sezione(
            I18n.tr("Fact-check", "Fact-check"),
            ul(
              items.map { it =>
                val verdetto = it.verdict.getOrElse("")
                val colore   = coloreVerdetto(verdetto)
                li(
                  cls := "factcheck-item",
                  span(cls := "verdetto", styleAttr := s"--colore-verdetto: $colore", verdetto),
                  h3("\u00ab" + it.claim.getOrElse("") + "\u00bb"),
                  p(it.evidence.getOrElse("")),
                  p(cls := "fonte", it.source.getOrElse("")),
                  p(it.verdict_explain.getOrElse("")),
                )
              },
            ),
          ),
        ),
        r.legal.filter(_.nonEmpty).map(items =>
          sezione(
            I18n.tr("Quadro giuridico", "Legal framework"),
            ul(items.map(it => li(cls := "legale-item", h3(it.heading.getOrElse("")), p(it.text.getOrElse(""))))),
          ),
        ),
        r.counter_narrative.filter(_.nonEmpty).map(items =>
          sezione(
            I18n.tr("Contro-narrazione", "Counter-narrative"),
            ul(
              items.map(it =>
                li(
                  cls := "contronarrazione-item",
                  h3(it.title.getOrElse("")),
                  p(it.text.getOrElse("")),
                  p(cls := "fonte", it.source.getOrElse("")),
                ),
              ),
            ),
          ),
        ),
        r.slides.filter(_.nonEmpty).map(items =>
          sezione(
            I18n.tr("Testo suggerito per slide social", "Suggested text for social slides"),
            p(
              cls := "nota-slide",
              child.text <-- I18n.tr(
                "Testo suggerito, da adattare graficamente per le tue slide social.",
                "Suggested text — adapt it visually for your own social slides.",
              ),
            ),
            div(
              cls := "slides",
              items.map(it =>
                div(
                  cls := "slide",
                  span(cls := "emoji", it.emoji.getOrElse("")),
                  h3(it.title.getOrElse("")),
                  p(it.body.getOrElse("")),
                ),
              ),
            ),
          ),
        ),
      )

    div(
      cls := "app",
      onMountCallback(_ => aggiornaQuota(token)),
      div(
        cls := "intestazione",
        div(
          cls := "marchio",
          img(src := "./acet-logo.svg", cls := "logo-acet", alt := "ACET"),
          div(
            cls := "nome-app",
            strong("Policy Decoder"),
            span("by ACET"),
          ),
        ),
        div(
          cls := "stato-utente",
          span(cls := "email-utente", child.text <-- emailSignal.map(_.getOrElse(""))),
          span(
            cls := "quota-chip",
            child.text <-- Signal.combine(quotaVar.signal, I18n.langVar.signal).map { case (qOpt, _) =>
              qOpt match
                case Some(q) if q.limite < 0 => I18n.now("Quota: illimitata", "Quota: unlimited")
                case Some(q) => I18n.now(
                    s"Quota: ${q.usate}/${q.limite} (rimanenti ${q.rimanenti})",
                    s"Quota: ${q.usate}/${q.limite} (remaining ${q.rimanenti})",
                  )
                case None => ""
            },
          ),
          I18n.switch,
          button(cls := "btn-esci", onClick --> (_ => logout()), child.text <-- I18n.tr("Esci", "Logout")),
        ),
      ),
      div(
        cls := "contenuto",
        div(
          cls := "tabs",
          button(
            cls.toggle("attivo") <-- modeVar.signal.map(_ == "testo"),
            onClick --> (_ => modeVar.set("testo")),
            child.text <-- I18n.tr("Testo", "Text"),
          ),
          button(
            cls.toggle("attivo") <-- modeVar.signal.map(_ == "url"),
            onClick --> (_ => modeVar.set("url")),
            "URL",
          ),
        ),
        div(
          child <-- modeVar.signal.map {
            case "url" =>
              input(
                typ := "url",
                placeholder := "https://...",
                onInput.mapToValue --> urlVar.writer,
                value <-- urlVar.signal,
              ): HtmlElement
            case _ =>
              textArea(
                rows := 12,
                maxLength := MAX_CARATTERI,
                placeholder <-- I18n.tr("Incolla qui il testo da analizzare...", "Paste the text you want to analyze here..."),
                onInput.mapToValue --> testoVar.writer,
                value <-- testoVar.signal,
              ): HtmlElement
          },
        ),
        div(
          cls := "controlli",
          label(child.text <-- I18n.tr("Lingua output: ", "Output language: ")),
          select(
            onChange.mapToValue --> linguaVar.writer,
            option(value := "auto", child.text <-- I18n.tr("Automatica", "Automatic")),
            option(value := "it", "Italiano"),
            option(value := "en", "English"),
          ),
          button(disabled <-- inCorsoVar.signal, onClick --> (_ => submit()), child.text <-- I18n.tr("Analizza", "Analyze")),
        ),
        child.maybe <-- inCorsoVar.signal.map(Option.when(_)(
          div(
            cls := "in-corso",
            div(cls := "spinner"),
            span(child.text <-- I18n.tr("Attendi che l'analisi venga elaborata...", "Please wait while the analysis is processed...")),
          ),
        )),
        child.maybe <-- erroreVar.signal.map(_.map(msg => p(cls := "errore", msg))),
        child.maybe <-- risultatoVar.signal.map(_.map { r =>
          val pulsanti: Seq[HtmlElement] = Seq(
            Some(
              button(
                typ := "button",
                disabled <-- pdfInCorsoVar.signal,
                onClick --> (_ => scaricaPdf("tutto")),
                child.text <-- I18n.tr("Scarica analisi (PDF)", "Download analysis (PDF)"),
              ),
            ),
            Option.when(r.slides.exists(_.nonEmpty))(
              button(
                typ := "button",
                disabled <-- pdfInCorsoVar.signal,
                onClick --> (_ => scaricaPdf("slide")),
                child.text <-- I18n.tr("Scarica testo per slide (PDF)", "Download slide text (PDF)"),
              ),
            ),
          ).flatten
          div(((cls := "azioni-download") +: pulsanti)*)
        }),
        child.maybe <-- risultatoVar.signal.map(_.map(renderRisultato)),
      ),
    )
