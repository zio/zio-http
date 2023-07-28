package zhttp.benchmarks

import java.util.concurrent.TimeUnit

import zio._

import zio.http._

import org.openjdk.jmh.annotations._

@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Fork(value = 1)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ServerInboundHandlerBenchmark {

  private val MAX      = 10000
  private val PAR      = 10
  private val res      = ZIO.succeed(Response.text("Hello World!"))
  private val endPoint = "test"
  private val http     = Routes(Route.route(Method.GET / endPoint)(handler(res))).toHttpApp

  def benchmarkZioParallel(): Task[Unit] =
    (for {
      port   <- Server.install(http)
      client <- ZIO.service[Client]
      url    <- ZIO.fromEither(URL.decode(s"http://localhost:$port/$endPoint"))
      call = client.request(Request(url = url))
      _ <- ZIO.collectAllParDiscard(List.fill(MAX)(call)).withParallelism(PAR)
    } yield ()).provide(Server.default, ZClient.default, zio.Scope.default)

  @Benchmark
  def benchmarkAppParallel(): Unit = {
    zio.Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(benchmarkZioParallel()))
  }
}
