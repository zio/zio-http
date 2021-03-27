package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpNestedFlatMapEval {
  implicit val canSupportPartial: CanSupportPartial[Int, String] = _ => "NOT_FOUND"

  private val MAX = 10000

  val programFlatMap: HttpChannel[Any, Nothing, Int, Int] = (0 to MAX).foldLeft(HttpChannel.identity[Int])((a, _) =>
    a.flatMap(i => HttpChannel.succeed(i + 1)),
  )

  @Benchmark
  def benchmarkHttpFlatMap(): Unit = {
    programFlatMap.eval(0)
    ()
  }
}
