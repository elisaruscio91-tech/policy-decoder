package decoder.frontend

import com.raquo.laminar.api.L.*
import org.scalajs.dom

// Lingua dell'interfaccia (non l'"outputLang" dell'analisi, che resta un
// concetto separato controllato dal selettore nella vista analisi). Scelta
// persistita in localStorage (sopravvive al logout, a differenza del token
// in sessionStorage), rilevata al primo avvio da navigator.language.
enum Lang:
  case It, En

object I18n:
  private val LANG_KEY = "policy-decoder-ui-lang"

  private def rilevaLinguaBrowser(): Lang =
    val nav = Option(dom.window.navigator.language).getOrElse("en")
    if nav.toLowerCase.startsWith("it") then Lang.It else Lang.En

  private def leggiSalvata(): Option[Lang] =
    Option(dom.window.localStorage.getItem(LANG_KEY)) match
      case Some("it") => Some(Lang.It)
      case Some("en") => Some(Lang.En)
      case _          => None

  val langVar: Var[Lang] = Var(leggiSalvata().getOrElse(rilevaLinguaBrowser()))

  def imposta(l: Lang): Unit =
    langVar.set(l)
    dom.window.localStorage.setItem(LANG_KEY, if l == Lang.It then "it" else "en")

  /** Codice lingua corrente ("it" | "en"), da passare al backend (es. export PDF). */
  def codice(): String = if langVar.now() == Lang.It then "it" else "en"

  /** Risolve una coppia (italiano, inglese) in un Signal reattivo sulla lingua UI corrente. */
  def tr(it: String, en: String): Signal[String] =
    langVar.signal.map(l => if l == Lang.It then it else en)

  /** Come tr, ma risolta subito (per messaggi costruiti in modo imperativo, es. errori). */
  def now(it: String, en: String): String =
    if langVar.now() == Lang.It then it else en

  def switch: HtmlElement =
    div(
      cls := "lingua-switch",
      button(
        typ := "button",
        cls.toggle("attivo") <-- langVar.signal.map(_ == Lang.It),
        onClick --> (_ => imposta(Lang.It)),
        "IT",
      ),
      button(
        typ := "button",
        cls.toggle("attivo") <-- langVar.signal.map(_ == Lang.En),
        onClick --> (_ => imposta(Lang.En)),
        "EN",
      ),
    )
