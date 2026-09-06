package decoder.backend.http

import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*

// Un solo backend HTTP condiviso (async-http-client su ZIO) per tutte le
// chiamate in uscita del server: motore IA e recupero articoli via URL.
object HttpClient:

  type Backend = SttpBackend[Task, Any]

  val layer: ZLayer[Any, Throwable, Backend] =
    ZLayer.scoped(AsyncHttpClientZioBackend.scoped())
