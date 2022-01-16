package zhttp.internal

import zhttp.http.URL.Location
import zhttp.http._
import zhttp.internal.DynamicServer.HttpEnv
import zhttp.internal.HttpRunnableSpec.HttpIO
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.test.DefaultRunnableSpec
import zio.{Chunk, Has, Queue, ZIO, ZManaged}

/**
 * Should be used only when e2e tests needs to be written which is typically for logic that is part of the netty based
 * backend. For most of the other use cases directly running the HttpApp should suffice. HttpRunnableSpec spins of an
 * actual Http server and makes requests.
 */
abstract class HttpRunnableSpec extends DefaultRunnableSpec { self =>
  def serve[R <: Has[_]](
    app: HttpApp[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory with DynamicServer, Nothing, Unit] =
    for {
      start <- Server.make(Server.app(app) ++ Server.port(0) ++ Server.paranoidLeakDetection).orDie
      _     <- DynamicServer.setStart(start).toManaged_
    } yield ()

  def request(
    path: Path = !!,
    method: Method = Method.GET,
    content: String = "",
    headers: Headers = Headers.empty,
  ): HttpIO[Any, Client.ClientResponse] = {
    for {
      port <- DynamicServer.getPort
      data = HttpData.fromString(content)
      response <- Client.request(
        Client.ClientRequest(method, URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)), headers, data),
        ClientSSLOptions.DefaultSSL,
      )
    } yield response
  }

  def status(method: Method = Method.GET, path: Path): HttpIO[Any, Status] = {
    for {
      port   <- DynamicServer.getPort
      status <- Client
        .request(
          method,
          URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)),
          ClientSSLOptions.DefaultSSL,
        )
        .map(_.status)
    } yield status
  }

  def webSocketRequest[R](
    path: Path = !!,
    headers: Headers = Headers.empty,
    app: SocketApp[R],
  ): ZIO[R with EventLoopGroup with ChannelFactory with DynamicServer, Throwable, Queue[WebSocketFrame]] = {
    // todo: uri should be created by using URL().asString but currently support for ws Scheme is missing
    for {
      port  <- DynamicServer.getPort
      queue <- Client.socket(s"ws://localhost:$port${path.asString}", headers, app)
    } yield queue
  }

  implicit class RunnableHttpAppSyntax(app: HttpApp[HttpEnv, Throwable]) {
    def deploy: ZIO[DynamicServer, Nothing, String] = DynamicServer.deploy(app)

    def request(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): HttpIO[Any, Client.ClientResponse] = for {
      id       <- deploy
      response <- self.request(path, method, content, Headers(DynamicServer.APP_ID, id) ++ headers)
    } yield response

    def requestBodyAsString(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): HttpIO[Any, String] =
      request(path, method, content, headers).flatMap(_.getBodyAsString)

    def requestHeaderValueByName(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    )(name: CharSequence): HttpIO[Any, Option[String]] =
      request(path, method, content, headers).map(_.getHeaderValue(name))

    def requestStatus(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): HttpIO[Any, Status] =
      request(path, method, content, headers).map(_.status)

//    def webSocketStatusCode(
//      path: Path = !!,
//      headers: Headers = Headers.empty,
//    ): HttpIO[SttpClient, Int] = for {
//      id  <- deploy
//      res <- self.webSocketRequest(path, Headers(DynamicServer.APP_ID, id) ++ headers)
//    } yield res.code.code

    def webSocketRequest[R](
      path: Path = !!,
      headers: Headers = Headers.empty,
      ss: SocketApp[R],
    ): ZIO[R with EventLoopGroup with ChannelFactory with DynamicServer, Throwable, Queue[WebSocketFrame]] = for {
      id  <- deploy
      res <- self.webSocketRequest(path, Headers(DynamicServer.APP_ID, id) ++ headers, ss)
    } yield res

    def requestBody(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): HttpIO[Any, Chunk[Byte]] =
      request(path, method, content, headers).flatMap(_.getBody)

    def requestContentLength(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): HttpIO[Any, Option[Long]] =
      request(path, method, content, headers).map(_.getContentLength)
  }
}

object HttpRunnableSpec {
  type HttpIO[-R, +A] =
    ZIO[R with EventLoopGroup with ChannelFactory with DynamicServer with ServerChannelFactory, Throwable, A]
}
