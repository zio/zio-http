package zio.http.netty

import zio._
import zio.test.TestAspect.{timeout, withLiveClock}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.stream.ZStream

import zio.http._
import zio.http.netty.NettyConfig.LeakDetectionLevel

import io.netty.buffer.{ByteBuf, Unpooled}

object NettyByteBufStreamBodySpec extends ZIOSpecDefault {

  private def direct(s: String): ByteBuf = {
    val buf = Unpooled.directBuffer(s.length)
    buf.writeBytes(s.getBytes("UTF-8"))
  }

  // A producer-owned registry: the stream carries indices, the body claims the buffer at write time.
  private final case class Registry(bufs: Chunk[ByteBuf], claimed: Ref.Synchronized[Set[Int]]) {
    def claim(i: Int): Option[ByteBuf] =
      Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe
          .run(claimed.modify(s => if (s(i)) (None, s) else (Some(bufs(i)), s + i)))
          .getOrThrow()
      }
  }

  private def registry(parts: Chunk[String]): UIO[Registry] =
    Ref.Synchronized.make(Set.empty[Int]).map(Registry(parts.map(direct), _))

  private def server(routes: Routes[Any, Response]): ZIO[Scope, Throwable, Int] =
    for {
      port <- Promise.make[Throwable, Int]
      _    <- Server
        .installRoutes(routes)
        .intoPromise(port)
        .zipRight(ZIO.never)
        .provide(
          ZLayer.succeed(NettyConfig.defaultWithFastShutdown.leakDetection(LeakDetectionLevel.PARANOID)),
          ZLayer.succeed(Server.Config.default.onAnyOpenPort),
          Server.customized,
        )
        .forkScoped
      p    <- port.await
    } yield p

  private def route(body: => Body): Routes[Any, Response] =
    Routes(Method.GET / "bufs" -> handler(ZIO.succeed(Response(status = Status.Ok, body = body))))

  private def get(port: Int): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.batched(Request.get(URL.decode(s"http://127.0.0.1:$port/bufs").toOption.get)))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("NettyBody.fromByteBufStream")(
      test("writes every claimed ByteBuf as a chunk over the socket; Netty releases each one after the write") {
        val parts = Chunk("alpha-", "beta-", "gamma-", "delta")
        for {
          reg  <- registry(parts)
          port <- server(route(NettyBody.fromByteBufStream(ZStream.range(0, parts.size))(reg.claim)))
          resp <- get(port)
          text <- resp.body.asString
        } yield assertTrue(
          resp.status == Status.Ok,
          resp.header(Header.TransferEncoding).contains(Header.TransferEncoding.Chunked),
          text == parts.mkString,
          reg.bufs.forall(_.refCnt() == 0),
        )
      },
      test("a stream that fails mid-way leaves its unclaimed buffers with the producer (none released by the body)") {
        val parts = Chunk("first-", "never-sent")
        for {
          reg  <- registry(parts)
          port <- server(
            route(
              NettyBody.fromByteBufStream(ZStream(0) ++ ZStream.fail(new RuntimeException("boom")))(reg.claim),
            ),
          )
          read <- get(port).flatMap(_.body.asString).exit
          _    <- ZIO.sleep(200.millis)
        } yield assertTrue(
          read.isFailure || read.exists(_.startsWith("first-")),
          reg.bufs(0).refCnt() == 0,
          reg.bufs(1).refCnt() == 1, // never claimed: the producer still owns it
        ) && assertTrue(reg.bufs(1).release())
      },
      test("the in-process read (asStream) claims each buffer, copies it to the heap and releases it") {
        val parts = Chunk("one", "two", "three")
        for {
          reg  <- registry(parts)
          text <- NettyBody.fromByteBufStream(ZStream.range(0, parts.size))(reg.claim).asString
        } yield assertTrue(text == "onetwothree", reg.bufs.forall(_.refCnt() == 0))
      },
    ).provideSome[Scope](
      ZLayer.succeed(ZClient.Config.default),
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      DnsResolver.default,
      Client.live,
    ) @@ withLiveClock @@ timeout(60.seconds)
}
