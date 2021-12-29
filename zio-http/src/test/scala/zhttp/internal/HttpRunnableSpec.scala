package zhttp.internal

import sttp.client3.asynchttpclient.zio.{SttpClient, send}
import sttp.client3.{Response => SResponse, UriContext, asWebSocketUnsafe, basicRequest}
import sttp.model.{Header => SHeader}
import sttp.ws.WebSocket
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.internal.AppCollection.HttpEnv
import zhttp.internal.HttpRunnableSpec.HttpIO
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.DefaultRunnableSpec
import zio.{Chunk, Has, Task, ZIO, ZManaged}

/**
 * Should be used only when e2e tests needs to be written which is typically for logic that is part of the netty based
 * backend. For most of the other use cases directly running the HttpApp should suffice. HttpRunnableSpec spins of an
 * actual Http server and makes requests.
 */
abstract class HttpRunnableSpec(port: Int) extends DefaultRunnableSpec { self =>

  def request(
    path: Path = !!,
    method: Method = Method.GET,
    content: String = "",
    headers: Headers = Headers.empty,
  ): HttpIO[Any, Client.ClientResponse] = {
    val data = HttpData.fromString(content)
    Client.request(
      Client.ClientParams(method, URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)), headers, data),
      ClientSSLOptions.DefaultSSL,
    )
  }

  def serve[R <: Has[_]](
    app: HttpApp[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Nothing, Unit] =
    Server.make(Server.app(app) ++ Server.port(port) ++ Server.paranoidLeakDetection).orDie

  def status(path: Path): HttpIO[Any, Status] =
    Client
      .request(
        Method.GET,
        URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)),
        ClientSSLOptions.DefaultSSL,
      )
      .map(_.status)

  def webSocketRequest(
    path: Path = !!,
    headers: Headers = Headers.empty,
  ): HttpIO[SttpClient, SResponse[Either[String, WebSocket[Task]]]] = {
    // todo: uri should be created by using URL().asString but currently support for ws Scheme is missing
    val url                       = s"ws://localhost:$port${path.asString}"
    val headerConv: List[SHeader] = headers.toList.map(h => SHeader(h._1, h._2))
    send(basicRequest.get(uri"$url").copy(headers = headerConv).response(asWebSocketUnsafe))
  }

  implicit class RunnableHttpAppSyntax(app: HttpApp[HttpEnv, Throwable]) {
    def deploy: ZIO[HttpAppCollection, Nothing, String] = AppCollection.deploy(app)

    def request(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): HttpIO[Any, Client.ClientResponse] = for {
      id       <- deploy
      response <- self.request(path, method, content, Headers(AppCollection.APP_ID, id) ++ headers)
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

    def webSocketStatusCode(
      path: Path = !!,
      headers: Headers = Headers.empty,
    ): HttpIO[SttpClient, Int] =
      for {
        id  <- deploy
        res <- self.webSocketRequest(path, Headers(AppCollection.APP_ID, id) ++ headers)
      } yield res.code.code

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
    ZIO[R with EventLoopGroup with ChannelFactory with HttpAppCollection with ServerChannelFactory, Throwable, A]
}
