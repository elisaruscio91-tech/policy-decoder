package decoder.frontend

import com.raquo.laminar.api.L.*
import decoder.shared.LoginRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object LoginView:

  def render(onSuccess: decoder.shared.LoginResponse => Unit, onRegistrati: () => Unit): HtmlElement =
    val emailVar    = Var("")
    val passwordVar = Var("")
    val codiceVar   = Var("")
    val erroreVar   = Var(Option.empty[String])
    val inCorsoVar  = Var(false)

    def submit(): Unit =
      erroreVar.set(None)
      inCorsoVar.set(true)
      val codice = Option(codiceVar.now().trim).filter(_.nonEmpty)
      Api.login(LoginRequest(emailVar.now().trim, passwordVar.now(), codice)).onComplete {
        case Success(Right(resp)) =>
          inCorsoVar.set(false)
          onSuccess(resp)
        case Success(Left((_, err))) =>
          inCorsoVar.set(false)
          erroreVar.set(Some(err.error))
        case Failure(e) =>
          inCorsoVar.set(false)
          erroreVar.set(Some(e.getMessage))
      }

    div(
      cls := "login",
      I18n.switch,
      div(
        cls := "marchio",
        img(src := "./acet-logo.svg", cls := "logo-acet", alt := "ACET"),
      ),
      h1("Policy Decoder"),
      p(
        cls := "intro-landing",
        child.text <-- I18n.tr(
          "Policy Decoder analizza testi e articoli per individuare tecniche retoriche, verifiche fattuali e il quadro giuridico, aiutandoti a leggere le policy con occhio critico.",
          "Policy Decoder analyzes texts and articles to spot rhetorical techniques, fact checks and the legal framework, helping you read policies with a critical eye.",
        ),
      ),
      form(
        onSubmit.preventDefault --> (_ => submit()),
        div(
          label("Email"),
          input(typ := "email", required := true, onInput.mapToValue --> emailVar.writer, value <-- emailVar.signal),
        ),
        div(
          label("Password"),
          input(
            typ := "password",
            required := true,
            onInput.mapToValue --> passwordVar.writer,
            value <-- passwordVar.signal,
          ),
        ),
        div(
          label(child.text <-- I18n.tr("Codice 2FA (se abilitato)", "2FA code (if enabled)")),
          input(typ := "text", onInput.mapToValue --> codiceVar.writer, value <-- codiceVar.signal),
        ),
        child.maybe <-- erroreVar.signal.map(_.map(msg => div(cls := "errore", msg))),
        button(typ := "submit", disabled <-- inCorsoVar.signal, child.text <-- I18n.tr("Accedi", "Login")),
      ),
      button(
        typ := "button",
        cls := "link-secondario",
        onClick --> (_ => onRegistrati()),
        child.text <-- I18n.tr("Non hai un account? Registrati", "Don't have an account? Register"),
      ),
      p(
        cls := "nota-abbonamento",
        child.text <-- I18n.tr(
          "Dopo l'approvazione della richiesta di accesso è previsto il versamento di una quota di abbonamento o una donazione ad ACET: scrivi a elisa.ruscio@proton.me per i dettagli.",
          "Once your access request is approved, a subscription fee or a donation to ACET is required: write to elisa.ruscio@proton.me for details.",
        ),
      ),
    )
