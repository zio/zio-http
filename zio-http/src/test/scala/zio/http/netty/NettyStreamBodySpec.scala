package zio.http.netty

import zio._
import zio.test.TestAspect.withLiveClock
import zio.test.{Spec, TestEnvironment, assertTrue}

import zio.stream.{ZStream, ZStreamAspect}

import zio.http.ZClient.Config
import zio.http._
import zio.http.internal.HttpRunnableSpec
import zio.http.netty.NettyConfig.LeakDetectionLevel

object NettyStreamBodySpec extends HttpRunnableSpec {

  def app(streams: Iterator[ZStream[Any, Throwable, Byte]], len: Long) =
    Routes(
      Method.GET / "with-content-length" ->
        handler(
          http.Response(
            status = Status.Ok,
            // Content-Length header will be removed when the body is a stream
            headers = Headers(Header.ContentLength(len)),
            body = Body.fromStream(streams.next()),
          ),
        ),
    ).sandbox.toHttpApp

  private def server(
    streams: Iterator[ZStream[Any, Throwable, Byte]],
    bodyLength: Long,
  ): ZIO[Any, Throwable, Int] =
    for {
      portPromise <- Promise.make[Throwable, Int]
      _           <- Server
        .install(app(streams, bodyLength))
        .intoPromise(portPromise)
        .zipRight(ZIO.never)
        .provide(
          ZLayer.succeed(NettyConfig.default.leakDetection(LeakDetectionLevel.PARANOID)),
          ZLayer.succeed(Server.Config.default.onAnyOpenPort),
          Server.customized,
        )
        .fork
      port        <- portPromise.await
    } yield port

  val singleConnectionClient: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (ZLayer.succeed(Config.default.copy(connectionPool = ConnectionPoolConfig.Fixed(1))) ++ ZLayer.succeed(
      NettyConfig.default,
    ) ++
      DnsResolver.default) >>> Client.live
  }

  def makeRequest(client: Client, port: Int) = client
    .request(
      Request.get(URL.decode(s"http://localhost:$port/with-content-length").toOption.get),
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Netty server")(
      test("should handle two requests by streaming body responses in the same connection") {
        val message = "streaming"
        for {
          atLeastOneChunkReceived  <- Promise.make[Nothing, Unit]
          // first response will be queued so we can manipulate streaming response end moment
          firstResponseQueue       <- Queue.unbounded[Byte]
          port                     <- server(
            List(
              ZStream.fromQueue(firstResponseQueue) @@ ZStreamAspect.rechunk(message.length / 3),
              ZStream.fromIterable(message.getBytes),
            ).iterator,
            message.length,
          )
          client                   <- ZIO.service[Client]
          firstResponse            <- makeRequest(client, port)
          firstResponseBodyReceive <- firstResponse.body.asStream.chunks.mapZIO { chunk =>
            atLeastOneChunkReceived.succeed(()) *> ZIO.succeed(chunk.asString)
          }.runCollect.fork
          _                        <- firstResponseQueue.offerAll(message.getBytes.toList)
          _                        <- atLeastOneChunkReceived.await
          // saying that there will be no more data in the first response stream
          _                        <- firstResponseQueue.shutdown
          // sending second request to make sure that the server wrote LastHttpContent.EMPTY_LAST_CONTENT
          // and is ready to handle one more request. Otherwise
          // "io.netty.handler.codec.EncoderException:
          // java.lang.IllegalStateException: unexpected message type: LastHttpContent"
          // exception will be thrown
          secondResponse           <- makeRequest(client, port)
          secondResponseBody       <- secondResponse.body.asStream.chunks.map(_.asString).runCollect
          firstResponseBody        <- firstResponseBodyReceive.join
        } yield {
          assertTrue(
            firstResponse.status == Status.Ok,
            firstResponse.headers.get(Header.ContentLength).isEmpty,
            firstResponse.headers.get(Header.TransferEncoding) == Some(Header.TransferEncoding.Chunked),
            firstResponseBody.reduce(_ + _) == message,
            secondResponse.status == Status.Ok,
            secondResponse.headers.get(Header.ContentLength).isEmpty,
            secondResponse.headers.get(Header.TransferEncoding) == Some(Header.TransferEncoding.Chunked),
            secondResponseBody == Chunk(message, ""),
          )
        }
      },
    ).provide(
      singleConnectionClient,
      Scope.default,
    ) @@ withLiveClock
}
