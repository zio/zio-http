package zhttp.service

import io.netty.handler.ssl.SslContext
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions
import zio.test.DefaultRunnableSpec
import zio.{Chunk, ZIO, ZManaged}

abstract class HttpRunnableSpec(port: Int) extends DefaultRunnableSpec {

  def serve[R](
    app: HttpApp[R, Throwable],
  ): ZManaged[R, Throwable, Unit] =
    Server.make(Server.app(app) ++ Server.port(port)).orDie

  def serve1[R](
    app: HttpApp[R, Throwable],
  ): ZManaged[R, Throwable, Unit] =
    Server.make(Server.app(app) ++ Server.port(port))

  def serveWithSSL[R](
    app: HttpApp[R, Throwable],
    sslContext: SslContext,
  ): ZManaged[R, Throwable, Unit] =
    Server.make(Server.app(app) ++ Server.port(port) ++ Server.ssl(ServerSSLOptions(sslContext))).orDie

  def status(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, Status] =
    requestPath(path).map(_.status)

  def requestPath(path: Path): ZIO[EventLoopGroup with ChannelFactory, Throwable, Client.ClientResponse] =
    Client.request(
      Method.GET -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)),
      ClientSSLOptions.DefaultSSL,
    )

  def headers(
    path: Path,
    method: Method,
    content: String,
    headers: (CharSequence, CharSequence)*,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, List[Header]] =
    request(path, method, content, headers.map(h => Header.custom(h._1.toString(), h._2)).toList).map(_.headers)

  def request(
    path: Path,
    method: Method,
    content: String,
    headers: List[Header] = Nil,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, Client.ClientResponse] = {
    val data = HttpData.fromChunk(Chunk.fromArray(content.getBytes(HTTP_CHARSET)))
    Client.request(
      Client.ClientParams(method -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)), headers, data),
      ClientSSLOptions.DefaultSSL,
    )
  }
}
