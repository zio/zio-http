package zio.http.endpoint

import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME
import java.time.{Instant, LocalDateTime}

import scala.util.Try

import zio._
import zio.test._

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint.AuthType.None
import zio.http.netty.NettyConfig

object ServerSentEventEndpointSpec extends ZIOHttpSpec {

  object StringPayload {
    val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], AuthType.None] =
      Endpoint(Method.GET / "sse" / "stream")
        .outStream[ServerSentEvent[String]](MediaType.text.`event-stream`)
        .inCodec(HttpCodec.header(Header.Accept).const(Header.Accept(MediaType.text.`event-stream`)))

    val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
      ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

    val sseRoute: Route[Any, Nothing] = sseEndpoint.implementHandler(Handler.succeed(stream))

    val routes: Routes[Any, Response] =
      sseRoute.toRoutes @@ Middleware.requestLogging(logRequestBody = true) @@ Middleware.debug

    val server: ZIO[Server, Throwable, Nothing] =
      Server.serveRoutes(routes)

    def locator(port: Int): EndpointLocator = EndpointLocator.fromURL(url"http://localhost:$port")

    private val invocation
      : Invocation[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], AuthType.None] =
      sseEndpoint(())

    def client(port: Int): ZIO[Client, Throwable, Chunk[ServerSentEvent[String]]] = ZIO.scoped {
      for {
        client <- ZIO.service[Client]
        executor = EndpointExecutor(client, locator(port))
        stream <- executor(invocation)
        events <- stream.take(5).runCollect
      } yield events
    }
  }

  object JsonPayload {
    case class Payload(timeStamp: Instant, message: String)

    object Payload {
      implicit val schema: Schema[Payload] = DeriveSchema.gen[Payload]
    }

    val stream: ZStream[Any, Nothing, ServerSentEvent[Payload]] =
      ZStream.repeatWithSchedule(ServerSentEvent(Payload(Instant.now(), "message")), Schedule.spaced(1.second))

    val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[Payload]], AuthType.None] =
      Endpoint(Method.GET / "sse" / "json-stream")
        .outStream[ServerSentEvent[Payload]]
        .inCodec(HttpCodec.header(Header.Accept).const(Header.Accept(MediaType.text.`event-stream`)))

    val sseRoute: Route[Any, Nothing] = sseEndpoint.implementHandler(Handler.succeed(stream))

    val routes: Routes[Any, Response] = sseRoute.toRoutes

    val server: URIO[Server, Nothing] =
      Server.serveRoutes(routes)

    def locator(port: Int): EndpointLocator = EndpointLocator.fromURL(url"http://localhost:$port")

    private val invocation
      : Invocation[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[Payload]], AuthType.None] =
      sseEndpoint(())

    def client(port: Int): ZIO[Client, Throwable, Chunk[ServerSentEvent[Payload]]] = ZIO.scoped {
      for {
        client <- ZIO.service[Client]
        executor = EndpointExecutor(client, locator(port))
        stream <- executor(invocation)
        events <- stream.take(5).runCollect
      } yield events
    }
  }

  object NonStreaming {
    val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ServerSentEvent[String], AuthType.None] =
      Endpoint(Method.GET / "sse" / "non-streaming")
        .out[ServerSentEvent[String]]
        .outHeader(HttpCodec.contentType.const(Header.ContentType(MediaType.text.`event-stream`)))

    val sseRoute: Route[Any, Nothing] = sseEndpoint.implementHandler(Handler.succeed(ServerSentEvent("Hello World")))

    val routes: Routes[Any, Response] = sseRoute.toRoutes

    val server: URIO[Server, Nothing] =
      Server.serveRoutes(routes)

    def locator(port: Int): EndpointLocator = EndpointLocator.fromURL(url"http://localhost:$port")

    private val invocation: Invocation[Unit, Unit, ZNothing, ServerSentEvent[String], AuthType.None] =
      sseEndpoint(())

    def client(port: Int): ZIO[Client, Nothing, ServerSentEvent[String]] = ZIO.scoped {
      for {
        client <- ZIO.service[Client]
        executor = EndpointExecutor(client, locator(port))
        event <- executor(invocation)
      } yield event
    }
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerSentEventSpec")(
      test("Send and receive ServerSentEvent with string payload") {
        import StringPayload._
        for {
          _      <- server.fork
          port   <- ZIO.serviceWithZIO[Server](_.port)
          events <- client(port)
        } yield assertTrue(events.size == 5 && events.forall(event => Try(ISO_LOCAL_TIME.parse(event.data)).isSuccess))
      },
      test("Send and receive ServerSentEvent with json payload") {
        import JsonPayload._
        for {
          _      <- server.fork
          port   <- ZIO.serviceWithZIO[Server](_.port)
          events <- client(port)
        } yield assertTrue(events.size == 5 && events.forall(event => Try(event.data.timeStamp).isSuccess))
      },
      test("Send and receive ServerSentEvent with non-streaming endpoint") {
        import NonStreaming._
        for {
          _     <- server.fork
          port  <- ZIO.serviceWithZIO[Server](_.port)
          event <- client(port)
        } yield assertTrue(event.data == "Hello World")
      },
    )
      .provideSomeLayer[Client & Server.Config & NettyConfig](
        (ZLayer.fromFunction[Server.Config => ServerRuntimeConfig](ServerRuntimeConfig(_)) ++ ZLayer
          .service[NettyConfig]) >>> Server.customized,
      )
      .provideShared(
        Client.live,
        ZLayer.succeed(Server.Config.default.port(0)),
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        ZLayer.succeed(ZClient.Config.default),
        DnsResolver.default,
      ) @@ TestAspect.withLiveClock
}
