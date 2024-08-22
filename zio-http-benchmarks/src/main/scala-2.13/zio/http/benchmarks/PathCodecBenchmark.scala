package zio.http.benchmarks

import java.util.concurrent.TimeUnit

import scala.collection.immutable.ListMap

import zio.{Scope => _, _}

import zio.http.RoutePattern.Tree
import zio.http._

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
private[http] class PathCodecBenchmark {

  val routesConflictLiteralAndText: Chunk[RoutePattern[_]] = Chunk(
    // two conflicts
    Method.GET / "orders" / "param1" / "literal1" / "p1" / "tail1",
    Method.GET / "orders" / "param1" / "literal1" / string("p2") / "tail2",
    Method.GET / "orders" / string("param") / "literal1" / "p1" / "tail3",
    Method.GET / "orders" / string("param") / "literal1" / string("p2") / "tail4",
  )

  var treeConflictLiteralAndText: Tree[Int] = Tree(ListMap.empty)

  routesConflictLiteralAndText.zipWithIndexFrom(1).foreach { case (routePattern, idx) =>
    treeConflictLiteralAndText = treeConflictLiteralAndText.add(routePattern, idx)
  }

  @Benchmark
  def collisionLiteralAndDynamicBenchmark4(): Unit = {
    val _ = treeConflictLiteralAndText.get(Method.GET, Path("/orders/param1/literal1/p1/tail4"))
  }

}
