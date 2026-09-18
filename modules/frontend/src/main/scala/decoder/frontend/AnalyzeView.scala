package decoder.frontend

import com.raquo.laminar.api.L.*
import decoder.shared.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

// Vista principale "minimo minimo": tab testo/url, selettore lingua di
// output, invio analisi, rendering delle 7 sezioni del risultato. Nessuna
// pretesa di replicare pixel-per-pixel il vecchio PolicyDecoder.tsx (645
// righe di React): stessa logica, presentazione essenziale.
object AnalyzeView:

  private val MAX_CARATTERI = 20_000

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
            erroreVar.set(Some(msg))
          case Failure(e) =>
            inCorsoVar.set(false)
            erroreVar.set(Some(e.getMessage))
        }

    def sezione(titolo: String, contenuto: Modifier[HtmlElement]*): HtmlElement =
      div(cls := "sezione", h2(titolo), div(contenuto*))

    def renderRisultato(r: RisultatoAnalisi): HtmlElement =
      div(
        cls := "risultato",
        r.summary.map(s =>
          sezione(
            "Sintesi",
            p(s.text.getOrElse("")),
            p(b("Intento dichiarato: "), s.declared_intent.getOrElse("")),
            p(b("Intento reale: "), s.actual_intent.getOrElse("")),
          ),
        ),
        r.danger_score.map(ds =>
          sezione(
            "Livello di rischio",
            p(b(s"${ds.score.getOrElse(0)}/100 — ${ds.label.getOrElse("")}")),
            p(ds.explanation.getOrElse("")),
          ),
        ),
        r.rhetoric.filter(_.nonEmpty).map(items =>
          sezione(
            "Tecniche retoriche",
            ul(
              items.map(it =>
                li(
                  p(b(s"${it.framework.getOrElse("")} — ${it.technique.getOrElse("")} (gravità ${it.severity.getOrElse(0)}/10)")),
                  p(cls := "quote", "\u00ab" + it.quote.getOrElse("") + "\u00bb"),
                  p(it.analysis.getOrElse("")),
                ),
              ),
            ),
          ),
        ),
        r.factcheck.filter(_.nonEmpty).map(items =>
          sezione(
            "Fact-check",
            ul(
              items.map(it =>
                li(
                  p(b(s"${it.verdict.getOrElse("")}: "), it.claim.getOrElse("")),
                  p(it.evidence.getOrElse("")),
                  p(cls := "fonte", it.source.getOrElse("")),
                  p(it.verdict_explain.getOrElse("")),
                ),
              ),
            ),
          ),
        ),
        r.legal.filter(_.nonEmpty).map(items =>
          sezione(
            "Quadro giuridico",
            ul(items.map(it => li(p(b(it.heading.getOrElse(""))), p(it.text.getOrElse(""))))),
          ),
        ),
        r.counter_narrative.filter(_.nonEmpty).map(items =>
          sezione(
            "Contro-narrazione",
            ul(
              items.map(it =>
                li(p(b(it.title.getOrElse(""))), p(it.text.getOrElse("")), p(cls := "fonte", it.source.getOrElse(""))),
              ),
            ),
          ),
        ),
        r.slides.filter(_.nonEmpty).map(items =>
          sezione(
            "Slide social",
            div(
              cls := "slides",
              items.map(it => div(cls := "slide", p(it.emoji.getOrElse("")), h3(it.title.getOrElse("")), p(it.body.getOrElse("")))),
            ),
          ),
        ),
      )

    div(
      cls := "app",
      onMountCallback(_ => aggiornaQuota(token)),
      div(
        cls := "intestazione",
        span(child.text <-- emailSignal.map(_.getOrElse(""))),
        span(
          child.text <-- quotaVar.signal.map {
            case Some(q) if q.limite < 0 => "Quota: illimitata"
            case Some(q)                 => s"Quota: ${q.usate}/${q.limite} (rimanenti ${q.rimanenti})"
            case None                    => ""
          },
        ),
        button(onClick --> (_ => logout()), "Esci"),
      ),
      div(
        cls := "tabs",
        button(
          cls.toggle("attivo") <-- modeVar.signal.map(_ == "testo"),
          onClick --> (_ => modeVar.set("testo")),
          "Testo",
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
              placeholder := "Incolla qui il testo da analizzare...",
              onInput.mapToValue --> testoVar.writer,
              value <-- testoVar.signal,
            ): HtmlElement
        },
      ),
      div(
        label("Lingua output: "),
        select(
          onChange.mapToValue --> linguaVar.writer,
          option(value := "auto", "Automatica"),
          option(value := "it", "Italiano"),
          option(value := "en", "English"),
        ),
      ),
      button(disabled <-- inCorsoVar.signal, onClick --> (_ => submit()), "Analizza"),
      child.maybe <-- inCorsoVar.signal.map(Option.when(_)(p(cls := "in-corso", "Analisi in corso..."))),
      child.maybe <-- erroreVar.signal.map(_.map(msg => p(cls := "errore", msg))),
      child.maybe <-- risultatoVar.signal.map(_.map(renderRisultato)),
    )
