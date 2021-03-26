package zhttp.service

import zhttp.http.URL.Location
import zhttp.http._
import zio.test.DefaultRunnableSpec
import zio.{Has, ZIO, ZManaged}

trait HttpRunnableSpec extends DefaultRunnableSpec {
  def serve[R <: Has[_], E: SilentResponse](
    app: Http[R, E],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Nothing, Unit] =
    Server.make(Server.app(app) ++ Server.port(8081)).orDie

  def status(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, Status] =
    requestPath(path).map(_.status)

  def requestPath(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    Client.request(Method.GET -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", 8081)))
}
