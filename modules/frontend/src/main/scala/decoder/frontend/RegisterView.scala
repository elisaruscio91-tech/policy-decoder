package decoder.frontend

import com.raquo.laminar.api.L.*
import decoder.shared.RichiediAccessoRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

// Form "Registrati": crea una richiesta di accesso (tabella richiesta_accesso
// sul backend), non un account attivo — l'utente resta in attesa di
// approvazione manuale (email di conferma quando approvato, vedi
// EndpointsImpl.richiediAccessoLogic / decoder.backend.cli.GestisciRichieste).
object RegisterView:

  def render(onTornaLogin: () => Unit): HtmlElement =
    val emailVar        = Var("")
    val passwordVar     = Var("")
    val confermaVar     = Var("")
    val erroreVar       = Var(Option.empty[String])
    val messaggioOkVar  = Var(Option.empty[String])
    val inCorsoVar      = Var(false)

    def submit(): Unit =
      erroreVar.set(None)
      messaggioOkVar.set(None)
      if passwordVar.now() != confermaVar.now() then
        erroreVar.set(Some(I18n.now("Le due password non coincidono.", "The two passwords do not match.")))
      else if passwordVar.now().length < 8 then
        erroreVar.set(Some(I18n.now("La password deve avere almeno 8 caratteri.", "Password must be at least 8 characters.")))
      else
        inCorsoVar.set(true)
        Api.richiediAccesso(RichiediAccessoRequest(emailVar.now().trim, passwordVar.now())).onComplete {
          case Success(Right(msg)) =>
            inCorsoVar.set(false)
            messaggioOkVar.set(Some(msg.messaggio))
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
      h2(child.text <-- I18n.tr("Richiedi accesso", "Request access")),
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
          label(child.text <-- I18n.tr("Conferma password", "Confirm password")),
          input(
            typ := "password",
            required := true,
            onInput.mapToValue --> confermaVar.writer,
            value <-- confermaVar.signal,
          ),
        ),
        child.maybe <-- erroreVar.signal.map(_.map(msg => div(cls := "errore", msg))),
        child.maybe <-- messaggioOkVar.signal.map(_.map(msg => div(cls := "messaggio-ok", msg))),
        button(
          typ := "submit",
          disabled <-- inCorsoVar.signal,
          child.text <-- I18n.tr("Invia richiesta", "Send request"),
        ),
      ),
      button(
        typ := "button",
        cls := "link-secondario",
        onClick --> (_ => onTornaLogin()),
        child.text <-- I18n.tr("Hai già un account? Accedi", "Already have an account? Log in"),
      ),
      p(
        cls := "nota-abbonamento",
        child.text <-- I18n.tr(
          "Dopo l'approvazione della richiesta di accesso è previsto il versamento di una quota di abbonamento o una donazione ad ACET: scrivi a elisa.ruscio@proton.me per i dettagli.",
          "Once your access request is approved, a subscription fee or a donation to ACET is required: write to elisa.ruscio@proton.me for details.",
        ),
      ),
    )
