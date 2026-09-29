package decoder.frontend

import com.raquo.laminar.api.L.*
import decoder.shared.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

// Componente radice: mostra LoginView o AnalyzeView a seconda della presenza
// di un token JWT valido. Stato di sessione tenuto qui, persistito in
// sessionStorage tramite Api (vedi Api.scala per il trade-off rispetto a un
// cookie httpOnly: "logout" è solo "il frontend dimentica il token").
object App:

  private enum Vista:
    case Accesso, Registrazione

  private val tokenVar = Var(Api.leggiToken())
  private val emailVar = Var(Api.leggiEmail())
  private val quotaVar = Var(Option.empty[Quota])
  private val vistaVar = Var[Vista](Vista.Accesso)

  private def logout(): Unit =
    Api.rimuoviToken()
    Api.rimuoviEmail()
    tokenVar.set(None)
    emailVar.set(None)
    quotaVar.set(None)

  private def onLoginSuccess(resp: LoginResponse): Unit =
    Api.salvaToken(resp.token)
    Api.salvaEmail(resp.email)
    tokenVar.set(Some(resp.token))
    emailVar.set(Some(resp.email))
    quotaVar.set(Some(resp.quota))

  // Richiamata sia al mount (se un token è già in sessionStorage da una
  // sessione precedente) sia dopo ogni analisi: se il token è scaduto o non
  // valido, il server risponde 401 e qui si torna alla schermata di login.
  private def aggiornaQuota(token: String): Unit =
    Api.usage(token).onComplete {
      case Success(Right(q))  => quotaVar.set(Some(q))
      case Success(Left(_))   => logout()
      case Failure(_)         => ()
    }

  val root: HtmlElement =
    div(
      child <-- tokenVar.signal.combineWith(vistaVar.signal).map {
        case (Some(token), _) => AnalyzeView.render(token, emailVar.signal, quotaVar, () => logout(), aggiornaQuota)
        case (None, Vista.Accesso) =>
          LoginView.render(onLoginSuccess, () => vistaVar.set(Vista.Registrazione))
        case (None, Vista.Registrazione) =>
          RegisterView.render(() => vistaVar.set(Vista.Accesso))
      },
    )
