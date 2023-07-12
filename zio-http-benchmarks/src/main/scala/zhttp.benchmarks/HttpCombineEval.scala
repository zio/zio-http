package zio.benchmarks

import java.util.concurrent.TimeUnit

import zio.{Trace, Unsafe}

import zio.http._

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCombineEval {
  private val req  = Request.get("/foo")
  private val res  = Response.ok
  private val MAX  = 1000
  private val app  = Routes(Method.GET / "" -> handler(res))
  private val spec = (0 to MAX).foldLeft(app)((a, _) => a ++ app).toApp

  @Benchmark
  def empty(): Unit = {
    spec.runZIOOrNull(req)(Unsafe.unsafe, Trace.empty)
    ()
  }

  @Benchmark
  def ok(): Unit = {
    spec.runZIOOrNull(req)(Unsafe.unsafe, Trace.empty)
    ()
  }
}
