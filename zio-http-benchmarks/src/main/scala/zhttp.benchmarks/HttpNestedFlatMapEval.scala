package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpNestedFlatMapEval {

  private val MAX = 1000

  val programFlatMap: Http[Any, Nothing, Int, Int] =
    (0 to MAX).foldLeft(Http.identity[Int])((a, _) => a.flatMap(i => Http.succeed(i + 1)))

  @Benchmark
  def benchmarkHttpFlatMap(): Unit = {
    programFlatMap.execute(0)
    ()
  }
}
