package zhttp.service

import zhttp.http.URL.Location
import zio.{Has, ZIO, ZManaged}
import zhttp.http._
import zio.test.DefaultRunnableSpec

trait HttpRunnableSpec extends DefaultRunnableSpec {
  def serve[R <: Has[_], E: SilentResponse](
    app: Http[R, E, Request, Response],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Nothing, Unit] =
    Server.make(Server.app(app) ++ Server.port(8081)).orDie

  def status(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, Status] =
    requestPath(path).map(_.status)

  def requestPath(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response.HttpResponse] =
    Client.request(Method.GET -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", 8081)))
}
