package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._
import zio._
import java.util.concurrent.TimeUnit

import io.netty.util.AsciiString

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpRouteTextPerf {

  private val runtime = Runtime.default
  private val msg = "HELLO WORLD"
  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenResponse = Response
    .text(msg)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private val app          = Http.succeed(frozenResponse)
  private val req: Request = Request(Method.GET, URL(!!))
  private val httpProgram  = app.execute(req).toZIO
  private val UIOProgram   =  UIO(frozenResponse)

  @Benchmark
  def benchmarkHttpProgram(): Unit = {
    runtime.unsafeRun(httpProgram)
    ()
  }

  @Benchmark
  def benchmarkUIOProgram(): Unit = {
    runtime.unsafeRun(UIOProgram)
    ()
  }
}
