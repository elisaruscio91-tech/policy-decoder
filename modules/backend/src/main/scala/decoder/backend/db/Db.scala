package decoder.backend.db

import decoder.backend.config.AppConfig
import io.getquill.*
import io.getquill.jdbczio.Quill
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import zio.*

import java.net.URI
import javax.sql.DataSource

// DATABASE_URL arriva nel formato "postgres://user:pass@host:port/db" (stesso
// usato prima con Prisma). Il driver JDBC di Postgres vuole invece uno "jdbc:"
// url più utente/password separati: qui facciamo la conversione una volta sola.
object Db:

  private def buildDataSource(url: String): Task[DataSource] = ZIO.attempt {
    val uri = new URI(url)
    val userInfo = Option(uri.getUserInfo).getOrElse("").split(":", 2)
    val user = userInfo.headOption.getOrElse("")
    val password = userInfo.drop(1).headOption.getOrElse("")
    val host = uri.getHost
    val port = if uri.getPort > 0 then uri.getPort else 5432
    val db = uri.getPath.stripPrefix("/")
    val query = Option(uri.getQuery).map("?" + _).getOrElse("")

    val ds = new PGSimpleDataSource()
    ds.setUrl(s"jdbc:postgresql://$host:$port/$db$query")
    ds.setUser(user)
    ds.setPassword(password)
    ds
  }

  val dataSourceLayer: ZLayer[AppConfig, Throwable, DataSource] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[AppConfig](cfg => buildDataSource(cfg.databaseUrl)))

  /** Esegue le migrazioni Flyway (risorse .sql in db/migration) prima che il
    * server accetti richieste — nessun passaggio manuale da ricordarsi.
    */
  val migrate: RIO[DataSource, Unit] =
    ZIO.serviceWithZIO[DataSource] { ds =>
      ZIO.attemptBlocking {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
      }.unit
    }

  val quillLayer: ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase.type]] =
    Quill.Postgres.fromNamingStrategy(SnakeCase)

  type Ctx = Quill.Postgres[SnakeCase.type]
