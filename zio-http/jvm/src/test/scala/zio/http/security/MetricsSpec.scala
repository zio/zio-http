package zio.http.security

import zio._
import zio.metrics._
import zio.test.Assertion._
import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.netty.NettyConfig

/*
  Tests `Middleware.metrics` interaction with limits on request size.
  `Middleware.metrics` only registers requests passing Netty's filter.
 */
object MetricsSpec extends ZIOHttpSpec {

  override def aspects: Chunk[TestAspectPoly] =
    Chunk(TestAspect.timeout(120.seconds), TestAspect.timed)

  def testMetrics[A](name: String, maxReq: Int, init: A, inc: A => A, mkRequest0: Int => A => Request) = {

    val routes = Routes(
      Method.GET / PathCodec.trailing -> Handler.ok,
    ) @@ Middleware.metrics(
      extraLabels = Set(MetricLabel("test", name)),
    )

    val histogram = Metric
      .histogram(
        "http_request_duration_seconds",
        Middleware.defaultBoundaries,
      )
      .tagged("test", name)
      .tagged("path", "/...")
      .tagged("method", "GET")
      .tagged("status", "200")
    val total     = Metric.counterInt("http_requests_total").tagged("test", name)
    val totalOk   = total.tagged("path", "/...").tagged("method", "GET").tagged("status", "200")
    val totalBad  = total.tagged("path", "/...").tagged("method", "GET").tagged("status", "400")

    test(name) {
      for {
        port <- Server.installRoutes(routes)
        mkRequest = mkRequest0(port)
        _             <- ZIO.iterate((0, init))(_._1 < maxReq) { case (n, content) =>
          ZIO.scoped {
            Client.streaming(mkRequest(content)).flatMap { req =>
              if (req.status == Status.Ok) ZIO.unit @@ Metric.counter("received ok").tagged("test", name).fromConst(1)
              else ZIO.unit @@ Metric.counter("not received ok").tagged("test", name).fromConst(1)
            }
          } *> ZIO.succeed((n + 1, inc(content)))
        }
        durations     <- histogram.value
        totalOkCount  <- totalOk.value
        totalBadCount <- totalBad.value
        okReceived    <- Metric.counter("received ok").tagged("test", name).value
        otherReceived <- Metric.counter("not received ok").tagged("test", name).value
      } yield assertTrue(
        totalOkCount == okReceived,
        totalBadCount == MetricState.Counter(0),
        otherReceived.count + okReceived.count == maxReq,
        durations.max <= 0.2,
      )
    }
  }

  val spec: Spec[TestEnvironment with Scope, Any] = suite("MetricsSpec")(
    testMetrics[String](
      "infinite url",
      2000,
      "A" * 3000,
      _ ++ "A",
      port => path => Request.get(s"http://localhost:$port/$path"),
    ),
    testMetrics[String](
      "infinite small segments url",
      1000,
      "/A" * 1500,
      _ ++ "/A",
      port => path => Request.get(s"http://localhost:$port$path"),
    ),
    testMetrics[String](
      "infinite header",
      2000,
      "A" * 7000,
      _ ++ "A",
      port => header => Request.get(s"http://localhost:$port").addHeader(Header.Custom("n", header)),
    ),
    testMetrics[List[Header.Custom]](
      "infinite small segments header",
      1000,
      (0 until 1000).toList.map(s => Header.Custom(s"$s", "A")),
      (l: List[Header.Custom]) => Header.Custom(l.head.customName.toString ++ "A", "A") :: l,
      port => headers => Request.get(s"http://localhost:$port").addHeaders(Headers(headers: _*)),
    ),
    testMetrics[String](
      "infinite body",
      10,
      "A" * (1023 * 100),
      _ ++ "A",
      port => body => Request.post(s"http://localhost:$port", Body.fromString(body)),
    ),
    testMetrics[Form](
      "infinite multi-part form",
      10,
      Form(Chunk.fill(1300)(FormField.Simple("n", "A"))),
      _ + FormField.Simple("n", "A"),
      port => form => Request.post(s"http://localhost:$port", Body.fromMultipartForm(form, Boundary("-"))),
    ),
  ).provide(
    Server.customized,
    ZLayer.succeed(Server.Config.default),
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    Client.live,
    ZLayer.succeed(ZClient.Config.default.maxHeaderSize(15000).maxInitialLineLength(15000).disabledConnectionPool),
    DnsResolver.default,
  ) @@ TestAspect.sequential @@ TestAspect.withLiveClock

}
