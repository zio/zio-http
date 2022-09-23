package zio.benchmarks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._
import zio.http._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpRouteTextPerf {

  private val runtime = Runtime.default

  private val res          = Response.text("HELLO WORLD")
  private val app          = Http.succeed(res)
  private val req: Request = Request.get(URL(!!))
  private val httpProgram  = ZIO.foreachDiscard(0 to 1000) { _ => app.execute(req).toZIO }
  private val UIOProgram   = ZIO.foreachDiscard(0 to 1000) { _ => ZIO.succeed(res) }

  @Benchmark
  def benchmarkHttpProgram(): Unit = {
    runtime.unsafe.run(httpProgram)(implicitly[Trace], Unsafe.unsafe)
    ()
  }

  @Benchmark
  def benchmarkUIOProgram(): Unit = {
    runtime.unsafe.run(UIOProgram)(implicitly[Trace], Unsafe.unsafe)
    ()
  }
}
