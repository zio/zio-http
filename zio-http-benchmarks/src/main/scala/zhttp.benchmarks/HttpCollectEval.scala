package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.Unsafe
import zio.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX       = 10000
  private val app       = Http.collect[Int] { case 0 => 1 }
  private val http      = Http.collect[Request] { case _ -> !! / "text" => 1 }
  private val httpTotal = Handler
    .fromFunction[Request] {
      case _ -> !! / "text" => 1
      case _                => 0 // representing "not found"
    }

  private val base: PartialFunction[Int, Int] = { case 0 => 1 }
  private val baseTotal: Int => Int           = _ => 1

  @Benchmark
  def benchmarkApp(): Unit = {
    (0 to MAX).foreach(_ => app.runHExitOrNull(0)(Unsafe.unsafe))
    ()
  }

  @Benchmark
  def benchmarkHttp(): Unit = {
    (0 to MAX).foreach(_ => http.runHExitOrNull(Request.get(url = URL(!! / "text")))(Unsafe.unsafe))
    ()
  }

  @Benchmark
  def benchmarkHttpTotal(): Unit = {
    (0 to MAX).foreach(_ => httpTotal.toRoute.runHExitOrNull(Request.get(url = URL(!! / "text")))(Unsafe.unsafe))
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
