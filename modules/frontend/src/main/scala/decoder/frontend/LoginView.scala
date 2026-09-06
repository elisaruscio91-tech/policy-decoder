package decoder.frontend

import com.raquo.laminar.api.L.*
import decoder.shared.LoginRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object LoginView:

  def render(onSuccess: decoder.shared.LoginResponse => Unit): HtmlElement =
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
      h1("Policy Decoder"),
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
          label("Codice 2FA (se abilitato)"),
          input(typ := "text", onInput.mapToValue --> codiceVar.writer, value <-- codiceVar.signal),
        ),
        child.maybe <-- erroreVar.signal.map(_.map(msg => div(cls := "errore", msg))),
        button(typ := "submit", disabled <-- inCorsoVar.signal, "Accedi"),
      ),
    )
