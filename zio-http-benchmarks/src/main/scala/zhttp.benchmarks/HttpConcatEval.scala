package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpConcatEval {
  implicit val canSupportPartial: CanSupportPartial[Int, String] = _ => ""
  implicit val canConcatenate: CanConcatenate[Any]               = _ => true

  private val MAX = 1000

  val app: HttpChannel[Any, String, Int, String] = HttpChannel.collect[Int]({ case 0 => "A" })
  val spec                                       = (0 to MAX).foldLeft(app)((a, _) => a <> app)

  @Benchmark
  def benchmarkHttpFlatMap(): Unit = {
    spec.eval(-1)
    ()
  }
}
