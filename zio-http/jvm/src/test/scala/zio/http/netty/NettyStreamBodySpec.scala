package zio.http.netty

import zio._
import zio.test.TestAspect.withLiveClock
import zio.test.{Spec, TestEnvironment, assertTrue}

import zio.stream.{ZPipeline, ZStream, ZStreamAspect}

import zio.http.ZClient.Config
import zio.http._
import zio.http.internal.RoutesRunnableSpec
import zio.http.multipart.mixed.MultipartMixed
import zio.http.netty.NettyConfig.LeakDetectionLevel
import zio.http.netty.NettyStreamBodySpec.app

object NettyStreamBodySpec extends RoutesRunnableSpec {

  def app(streams: Iterator[ZStream[Any, Throwable, Byte]], len: Long) =
    Routes(
      Method.GET / "with-content-length" ->
        handler(
          http.Response(
            status = Status.Ok,
            body = Body.fromStream(streams.next(), len),
          ),
        ),
    ).sandbox

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
          ZLayer.succeed(NettyConfig.defaultWithFastShutdown.leakDetection(LeakDetectionLevel.PARANOID)),
          ZLayer.succeed(Server.Config.default.onAnyOpenPort),
          Server.customized,
        )
        .fork
      port        <- portPromise.await
    } yield port

  val singleConnectionClient: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (ZLayer.succeed(Config.default.copy(connectionPool = ConnectionPoolConfig.Fixed(1))) ++ ZLayer.succeed(
      NettyConfig.defaultWithFastShutdown,
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
            message.length.toLong,
          )
          client                   <- ZIO.service[Client]
          firstResponse            <- makeRequest(client, port)
          firstResponseBodyReceive <- firstResponse.body.asStream.chunks.mapZIO { chunk =>
            atLeastOneChunkReceived.succeed(()).as(chunk.asString)
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
          value =
            firstResponse.status == Status.Ok &&
              // since response has not chunked transfer encoding header we can't guarantee that
              // received chunks will be the same as it was transferred. So we need to check the whole body
              firstResponseBody.reduce(_ + _) == message &&
              secondResponse.status == Status.Ok &&
              secondResponseBody == Chunk(message)
        } yield {
          assertTrue(
            value,
          )
        }
      },
      test("properly decodes body's boundary") {
        def trackablePart(content: String): ZIO[Any, Nothing, (MultipartMixed.Part, Promise[Nothing, Boolean])] = {
          zio.Promise.make[Nothing, Boolean].map { p =>
            MultipartMixed.Part(
              Headers(Header.ContentType(MediaType.text.`plain`)),
              ZStream(content)
                .via(ZPipeline.utf8Encode)
                .ensuring(p.succeed(true)),
            ) ->
              p
          }
        }
        def trackableMultipartMixed(
          b: Boundary,
        )(partsContents: String*): ZIO[Any, Nothing, (MultipartMixed, Seq[Promise[Nothing, Boolean]])] = {
          ZIO
            .foreach(partsContents)(trackablePart)
            .map { tps =>
              val (parts, promisises) = tps.unzip
              val mpm                 = MultipartMixed.fromParts(ZStream.fromIterable(parts), b, 1)
              (mpm, promisises)
            }
        }

        def serve(resp: Response): ZIO[Any, Throwable, RuntimeFlags] = {
          val app = Routes(Method.GET / "it" -> handler(resp))
          for {
            portPromise <- Promise.make[Throwable, Int]
            _           <- Server
              .install(app)
              .intoPromise(portPromise)
              .zipRight(ZIO.never)
              .provide(
                ZLayer.succeed(NettyConfig.defaultWithFastShutdown.leakDetection(LeakDetectionLevel.PARANOID)),
                ZLayer.succeed(Server.Config.default.onAnyOpenPort),
                Server.customized,
              )
              .fork
            port        <- portPromise.await
          } yield port
        }

        for {
          mpmAndPromises <- trackableMultipartMixed(Boundary("this_is_a_boundary"))(
            "this is the boring part 1",
            "and this is the boring part two",
          )
          (mpm, promises) = mpmAndPromises
          resp            = Response(body =
            Body.fromStreamChunked(mpm.source).contentType(MediaType.multipart.`mixed`, mpm.boundary),
          )
            .addHeader(Header.ContentType(MediaType.multipart.`mixed`, Some(mpm.boundary)))
          port   <- serve(resp)
          client <- ZIO.service[Client]
          req = Request.get(s"http://localhost:$port/it")
          actualResp   <- client(req)
          actualMpm    <- actualResp.body.asMultipartMixed
          partsResults <- actualMpm.parts.zipWithIndex.mapZIO { case (part, idx) =>
            val pr = promises(idx.toInt)
            // todo: due to server side buffering can't really expect the promises to be uncompleted BEFORE pulling on the client side
            part.toBody.asString <*>
              pr.isDone
          }.runCollect
        } yield {
          zio.test.assertTrue {
            actualResp.headers(Header.ContentType) == resp.headers(Header.ContentType) &&
            actualResp.body.boundary == Some(mpm.boundary) &&
            actualMpm.boundary == mpm.boundary &&
            partsResults == Chunk(
              // todo: due to server side buffering can't really expect the promises to be uncompleted BEFORE pulling on the client side
              ("this is the boring part 1", true),
              ("and this is the boring part two", true),
            )
          }
        }
      },
    ).provide(
      singleConnectionClient,
      Scope.default,
    ) @@ withLiveClock
}
