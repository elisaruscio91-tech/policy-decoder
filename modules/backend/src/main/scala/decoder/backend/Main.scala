package decoder.backend

import decoder.backend.api.EndpointsImpl
import decoder.backend.config.AppConfig
import decoder.backend.db.{Db, Repo}
import decoder.backend.fonti.FontiRepo
import decoder.backend.http.HttpClient
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.*

// Entry point: un solo container serve sia le API JSON (sotto /api, via
// Tapir/zio-http) sia i file statici del frontend ScalaJS/Laminar compilato
// (prodotti dalla build sbt e copiati in resources/static durante la build
// Docker) — niente CORS, niente secondo servizio Nginx/Vite.
//
// NOTA: questo file collega zio-http (le cui API sono cambiate più volte tra
// versioni negli ultimi anni) e non ha potuto essere compilato in questo
// ambiente (nessuna toolchain JVM/sbt disponibile). È il punto più a rischio
// dell'intera riscrittura: verificare con `sbt compile` e correggere qui per
// primo in caso di errori di build.
object Main extends ZIOAppDefault:

  private val staticHandler: Handler[Any, Nothing, (Path, Request), Response] =
    Handler
      .fromFunctionHandler[(Path, Request)] { case (path, _) =>
        val richiesto = path.encode.stripPrefix("/")
        val risorsa   = if richiesto.isEmpty then "static/index.html" else s"static/$richiesto"
        Handler.fromResource(risorsa)
      }
      .orDie

  private val staticRoutes: Routes[Any, Response] =
    Routes(Method.GET / trailing -> staticHandler)

  // Endpoint di healthcheck: usato da Docker/Coolify per verificare che il
  // processo risponda davvero (non solo che sia partito) — vedi HEALTHCHECK
  // nel Dockerfile.
  private val healthRoutes: Routes[Any, Response] =
    Routes(Method.GET / "api" / "health" -> Handler.ok)

  private val porta: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)

  private val program =
    for
      _        <- Db.migrate
      apiRoutes = ZioHttpInterpreter().toHttp(EndpointsImpl.all)
      _        <- Server.serve(healthRoutes ++ apiRoutes ++ staticRoutes)
    yield ()

  override def run =
    program.provide(
      AppConfig.layer,
      Db.dataSourceLayer,
      Db.quillLayer,
      Repo.layer,
      FontiRepo.layer,
      HttpClient.layer,
      Server.defaultWith(
        _.port(porta).copy(requestStreaming = Server.RequestStreaming.Disabled(20 * 1024 * 1024)),
      ),
    )
