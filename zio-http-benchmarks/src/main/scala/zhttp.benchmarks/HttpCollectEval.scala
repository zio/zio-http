package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX  = 10000
  private val app  = Http.collect[Int] { case 0 => 1 }
  private val http = Http.collect[Request] { case _ -> !! / "text" => 1 }

  private val base: Int => Int = _ => 1

  @Benchmark
  def benchmarkApp(): Unit = {
    (0 to MAX).foreach(_ => app.execute(0))
    ()
  }

  @Benchmark
  def benchmarkHttp(): Unit = {
    (0 to MAX).foreach(_ => http.execute(Request.get(url = URL(!! / "text"))))
    ()
  }

  @Benchmark
  def benchmarkBase(): Unit = {
    (0 to MAX).foreach(_ => base(0))
    ()
  }
}
