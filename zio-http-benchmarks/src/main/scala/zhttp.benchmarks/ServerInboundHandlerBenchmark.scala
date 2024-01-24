package zhttp.benchmarks

import java.util.concurrent.TimeUnit

import zio._

import zio.http._

import org.openjdk.jmh.annotations._
import sttp.client3.{HttpURLConnectionBackend, UriContext, basicRequest}

@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ServerInboundHandlerBenchmark {
  private val random      = scala.util.Random
  random.setSeed(42)
  private val largeString = random.alphanumeric.take(100000).mkString

  private val baseUrl = "http://localhost:8080"
  private val headers = Headers(Header.ContentType(MediaType.text.`plain`).untyped)

  private val arrayEndpoint = "array"
  private val arrayResponse = ZIO.succeed(
    Response(
      status = Status.Ok,
      headers = headers,
      body = Body.fromArray(largeString.getBytes),
    ),
  )
  private val arrayRoute    = Route.route(Method.GET / arrayEndpoint)(handler(arrayResponse))
  private val arrayRequest  = basicRequest.get(uri"$baseUrl/$arrayEndpoint")

  private val chunkEndpoint = "chunk"
  private val chunkResponse = ZIO.succeed(
    Response(
      status = Status.Ok,
      headers = headers,
      body = Body.fromChunk(Chunk.fromArray(largeString.getBytes)),
    ),
  )
  private val chunkRoute    = Route.route(Method.GET / chunkEndpoint)(handler(chunkResponse))
  private val chunkRequest  = basicRequest.get(uri"$baseUrl/$chunkEndpoint")

  private val testResponse = ZIO.succeed(Response.text("Hello World!"))
  private val testEndPoint = "test"
  private val testRoute    = Route.route(Method.GET / testEndPoint)(handler(testResponse))
  private val testUrl      = s"$baseUrl/$testEndPoint"
  private val testRequest  = basicRequest.get(uri"$testUrl")

  private val testContentTypeRequest = testRequest.contentType("application/json; charset=utf8")

  private val shutdownResponse = Response.text("shutting down")
  private val shutdownEndpoint = "shutdown"
  private val shutdownUrl      = s"http://localhost:8080/$shutdownEndpoint"

  private val backend = HttpURLConnectionBackend()

  private def shutdownRoute(shutdownSignal: Promise[Nothing, Unit]) =
    Route.route(Method.GET / shutdownEndpoint)(handler(shutdownSignal.succeed(()).as(shutdownResponse)))
  private def http(shutdownSignal: Promise[Nothing, Unit])          =
    Routes(testRoute, arrayRoute, chunkRoute, shutdownRoute(shutdownSignal)).toHttpApp

  @Setup(Level.Trial)
  def setup(): Unit = {
    val startServer: Task[Unit] = (for {
      shutdownSignal <- Promise.make[Nothing, Unit]
      fiber          <- Server.serve(http(shutdownSignal)).fork
      _              <- shutdownSignal.await *> fiber.interrupt
    } yield ()).provideLayer(Server.default)

    val waitForServerStarted: Task[Unit] = (for {
      client <- ZIO.service[Client]
      _      <- client.request(Request(url = URL.decode(testUrl).toOption.get))
    } yield ()).provide(ZClient.default, zio.Scope.default)

    Unsafe.unsafe(implicit u => Runtime.default.unsafe.fork(startServer))
    Unsafe.unsafe(implicit u =>
      Runtime.default.unsafe.run(waitForServerStarted.retry(Schedule.fixed(1.second))).getOrThrow(),
    )
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit = {
    val stopServer = (for {
      client <- ZIO.service[Client]
      _      <- client.request(Request(url = URL.decode(shutdownUrl).toOption.get))
    } yield ()).provide(ZClient.default, zio.Scope.default)
    Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(stopServer).getOrThrow())
  }

  @Benchmark
  def benchmarkLargeArray(): Unit = {
    val statusCode = arrayRequest.send(backend).code
    if (!statusCode.isSuccess)
      throw new RuntimeException(s"Received unexpected status code ${statusCode.code}")
  }

  @Benchmark
  def benchmarkLargeChunk(): Unit = {
    val statusCode = chunkRequest.send(backend).code
    if (!statusCode.isSuccess)
      throw new RuntimeException(s"Received unexpected status code ${statusCode.code}")
  }

  @Benchmark
  def benchmarkSimple(): Unit = {
    val statusCode = testRequest.send(backend).code
    if (!statusCode.isSuccess)
      throw new RuntimeException(s"Received unexpected status code ${statusCode.code}")
  }

  @Benchmark
  def benchmarkSimpleContentType(): Unit = {
    val statusCode = testContentTypeRequest.send(backend).code
    if (!statusCode.isSuccess)
      throw new RuntimeException(s"Received unexpected status code ${statusCode.code}")
  }
}
