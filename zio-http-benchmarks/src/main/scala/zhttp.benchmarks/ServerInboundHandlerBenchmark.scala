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
  private val testResponse = ZIO.succeed(Response.text("Hello World!"))
  private val testEndPoint = "test"
  private val testRoute    = Route.route(Method.GET / testEndPoint)(handler(testResponse))
  private val testUrl      = s"http://localhost:8080/$testEndPoint"
  private val testRequest  = basicRequest.get(uri"$testUrl")

  private val shutdownResponse = Response.text("shutting down")
  private val shutdownEndpoint = "shutdown"
  private val shutdownUrl      = s"http://localhost:8080/$shutdownEndpoint"

  private val backend = HttpURLConnectionBackend()

  private def shutdownRoute(shutdownSignal: Promise[Nothing, Unit]) =
    Route.route(Method.GET / shutdownEndpoint)(handler(shutdownSignal.succeed(()).as(shutdownResponse)))
  private def http(shutdownSignal: Promise[Nothing, Unit]) = Routes(testRoute, shutdownRoute(shutdownSignal)).toHttpApp

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
  def benchmarkApp(): Unit = {
    val statusCode = testRequest.send(backend).code
    if (!statusCode.isSuccess)
      throw new RuntimeException(s"Received unexpected status code ${statusCode.code}")
  }
}
