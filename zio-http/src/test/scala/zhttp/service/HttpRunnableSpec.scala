package zhttp.service

import zhttp.http.HttpContent.Complete
import zhttp.http.URL.Location
import zhttp.http._
import zio.test.DefaultRunnableSpec
import zio.{Has, ZIO, ZManaged}

abstract class HttpRunnableSpec(port: Int) extends DefaultRunnableSpec {

  def serve[R <: Has[_], E: SilentResponse](
    app: Http[R, E],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Nothing, Unit] =
    Server.make(Server.app(app) ++ Server.port(port)).orDie

  def status(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, Status] =
    requestPath(path).map(_.status)

  def requestPath(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    Client.request(Method.GET -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)))

  def request(
    path: Path,
    method: Method,
    content: String,
    headers: List[Header] = Nil,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = {
    val data = Request.Data(headers, Complete[String](content))
    Client.request(Request(method -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)), data))
  }
}
