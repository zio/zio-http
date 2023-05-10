package zio.benchmarks

import java.util.concurrent.TimeUnit

import zio.{Trace, Unsafe}

import zio.http._

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX       = 10000
  private val app       = Http.collect[Int] { case 0 => 1 }
  private val http      = Http.collect[Request] { case _ -> Root / "text" => 1 }
  private val httpTotal = Handler
    .fromFunction[Request] {
      case _ -> Root / "text" => 1
      case _                  => 0 // representing "not found"
    }

  private val base: PartialFunction[Int, Int] = { case 0 => 1 }
  private val baseTotal: Int => Int           = _ => 1

  @Benchmark
  def benchmarkApp(): Unit = {
    (0 to MAX).foreach(_ => app.runZIOOrNull(0)(Unsafe.unsafe, Trace.empty))
    ()
  }

  @Benchmark
  def benchmarkHttp(): Unit = {
    (0 to MAX).foreach(_ => http.runZIOOrNull(Request.get(url = URL(Root / "text")))(Unsafe.unsafe, Trace.empty))
    ()
  }

  @Benchmark
  def benchmarkHttpTotal(): Unit = {
    (0 to MAX).foreach(_ =>
      httpTotal.toHttp.runZIOOrNull(Request.get(url = URL(Root / "text")))(Unsafe.unsafe, Trace.empty),
    )
    ()
  }

  @Benchmark
  def benchmarkBase(): Unit = {
    (0 to MAX).foreach(_ => base(0))
    ()
  }

  @Benchmark
  def benchmarkBaseTotal(): Unit = {
    (0 to MAX).foreach(_ => baseTotal(0))
    ()
  }
}
