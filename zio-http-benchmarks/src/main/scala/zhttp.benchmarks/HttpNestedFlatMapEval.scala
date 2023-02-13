package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http._
import zio.{Trace, Unsafe}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpNestedFlatMapEval {

  private val MAX = 1000

  val programFlatMap: Handler[Any, Nothing, Int, Int] =
    (0 to MAX).foldLeft(Handler.identity[Int])((a, _) => a.flatMap(i => Handler.succeed(i + 1)))

  @Benchmark
  def benchmarkHttpFlatMap(): Unit = {
    programFlatMap.toHttp.runZIOOrNull(0)(Unsafe.unsafe, Trace.empty)
    ()
  }
}
