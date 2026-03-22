package zio.http

import zio._

import zio.http.ZClient.Config
import zio.http.internal.FetchDriver

object FetchClientLayers {

  def live: ZLayer[ZClient.Config, Throwable, ZClient.Client] = {
    implicit val trace: Trace = Trace.empty
    FetchDriver.live >>> ZClient.customized
  }.fresh

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  )(implicit trace: Trace): ZLayer[Any, Throwable, ZClient.Client] =
    ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*)))
      .mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  def default: ZLayer[Any, Throwable, ZClient.Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Config.default) >>> live
  }

}
