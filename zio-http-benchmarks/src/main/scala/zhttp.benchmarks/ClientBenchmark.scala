package zhttp.benchmarks

import java.util.concurrent.TimeUnit

import scala.annotation.nowarn

import zio._

import zio.http._

import org.openjdk.jmh.annotations._

@nowarn
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(1)
class ClientBenchmark {
  private val random = scala.util.Random
  random.setSeed(42)

  private implicit val unsafe: Unsafe = Unsafe.unsafe(identity)

  @Param(Array("small", "large"))
  var path: String = _

  private val smallString = "Hello!".getBytes
  private val largeString = random.alphanumeric.take(10000).mkString.getBytes

  private val smallRequest = Request(url = url"http://0.0.0.0:8080/small")
  private val largeRequest = Request(url = url"http://0.0.0.0:8080/large")

  private val smallResponse = Response(status = Status.Ok, body = Body.fromArray(smallString))
  private val largeResponse = Response(status = Status.Ok, body = Body.fromArray(largeString))

  private val smallRoute = Route.route(Method.GET / "small")(handler(smallResponse))
  private val largeRoute = Route.route(Method.GET / "large")(handler(largeResponse))

  private val shutdownResponse = Response.text("shutting down")

  private def shutdownRoute(shutdownSignal: Promise[Nothing, Unit]) =
    Route.route(Method.GET / "shutdown")(handler(shutdownSignal.succeed(()).as(shutdownResponse)))

  private def http(shutdownSignal: Promise[Nothing, Unit]) =
    Routes(smallRoute, largeRoute, shutdownRoute(shutdownSignal))

  private val rtm     = Runtime.unsafe.fromLayer(ZClient.default)
  private val runtime = rtm.unsafe

  private def run(f: RIO[Client, Any]): Any = runtime.run(f).getOrThrow()

  @Setup(Level.Trial)
  def setup(): Unit = {
    val startServer: Task[Unit] = (for {
      shutdownSignal <- Promise.make[Nothing, Unit]
      fiber          <- Server.serve(http(shutdownSignal)).fork
      _              <- shutdownSignal.await *> fiber.interrupt
    } yield ()).provideLayer(Server.default)

    val waitForServerStarted: Task[Unit] = (for {
      client <- ZIO.service[Client]
      _      <- client.batched(smallRequest)
    } yield ()).provide(ZClient.default)

    run(startServer.forkDaemon *> waitForServerStarted.retry(Schedule.fixed(1.second)))
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit = {
    val stopServer = (for {
      client <- ZIO.service[Client]
      _      <- client.batched(Request(url = url"http://localhost:8080/shutdown"))
    } yield ()).provide(ZClient.default)
    run(stopServer)
    rtm.shutdown0()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def zhttpChunkBenchmark(): Any = run {
    val req = if (path == "small") smallRequest else largeRequest
    ZIO.serviceWithZIO[Client] { client =>
      client.batched(req).flatMap(_.body.asChunk).repeatN(100)
    }
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def zhttpStreamToChunkBenchmark(): Any = run {
    val req = if (path == "small") smallRequest else largeRequest
    ZIO.serviceWithZIO[Client] { client =>
      client.batched(req).flatMap(_.body.asStream.runCollect).repeatN(100)
    }
  }
}
