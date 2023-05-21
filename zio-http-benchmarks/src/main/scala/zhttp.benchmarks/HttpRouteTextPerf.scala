package zio.benchmarks

import java.util.concurrent.TimeUnit

import zio._

import zio.http._

import org.openjdk.jmh.annotations.{Scope => JScope, _}

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpRouteTextPerf {

  private val runtime = Runtime.default

  private val res          = Response.text("HELLO WORLD")
  private val app          = Handler.succeed(res)
  private val req: Request = Request.get(URL(Root))
  private val httpProgram  = ZIO.foreachDiscard(0 to 1000) { _ => app(req) }
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
