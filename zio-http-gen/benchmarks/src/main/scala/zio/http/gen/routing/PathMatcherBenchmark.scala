package zio.http.gen.routing

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class PathMatcherBenchmark {
  
  @Param(Array("10", "100", "1000"))
  var pathCount: Int = _
  
  var setMatcher: Set[String] = _
  var stateMatcher: PathMatcher = _
  var testPaths: Array[String] = _
  
  @Setup
  def setup(): Unit = {
    val paths = (1 to pathCount).map { i =>
      val depth = 1 + Random.nextInt(4)
      (0 until depth)
        .map(_ => Random.alphanumeric.take(5).mkString)
        .mkString("/", "/", "")
    }.toSet
    
    setMatcher = paths
    stateMatcher = PathMatcher.compile(paths)
    
    testPaths = (
      paths.take(pathCount / 2).toArray ++
      Array.fill(pathCount / 2)(s"/nonexistent/${Random.nextInt()}")
    )
  }
  
  @Benchmark
  def benchmarkSetMatcher(): Int = {
    var count = 0
    testPaths.foreach { path =>
      if (setMatcher.contains(path)) count += 1
    }
    count
  }
  
  @Benchmark
  def benchmarkStateMatcher(): Int = {
    var count = 0
    testPaths.foreach { path =>
      if (stateMatcher.matches(path)) count += 1
    }
    count
  }
}