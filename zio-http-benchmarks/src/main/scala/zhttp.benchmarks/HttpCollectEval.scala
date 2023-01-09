package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX       = 10000
  private val app       = Route.collect[Int] { case 0 => 1 }
  private val http      = Route.collect[Request] { case _ -> !! / "text" => 1 }
  private val httpTotal = Handler
    .fromFunction[Request] {
      case _ -> !! / "text" => 1
      case _                => 0 // representing "not found"
    }

  private val base: PartialFunction[Int, Int] = { case 0 => 1 }
  private val baseTotal: Int => Int           = _ => 1

  @Benchmark
  def benchmarkApp(): Unit = {
    (0 to MAX).foreach(_ => app.toHExitOrNull(0))
    ()
  }

  @Benchmark
  def benchmarkHttp(): Unit = {
    (0 to MAX).foreach(_ => http.toHExitOrNull(Request.get(url = URL(!! / "text"))))
    ()
  }

  @Benchmark
  def benchmarkHttpTotal(): Unit = {
    (0 to MAX).foreach(_ => httpTotal.toRoute.toHExitOrNull(Request.get(url = URL(!! / "text"))))
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
