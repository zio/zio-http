package zio.benchmarks

import java.util.concurrent.TimeUnit

import zio.{Trace, Unsafe}

import zio.http._

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX       = 10000
  private val req       = Request()
  private val res       = Response.ok
  private val app       = Routes.singleton(handler(res)).toHttpApp
  private val http      = Routes(Route.route(Method.ANY / "text")(handler(res))).toHttpApp
  
  private val base: PartialFunction[Int, Int] = { case 0 => 1 }
  private val baseTotal: Int => Int           = _ => 1

  @Benchmark
  def benchmarkApp(): Unit = {
    (0 to MAX).foreach(_ => app(req))
    ()
  }

  @Benchmark
  def benchmarkHttp(): Unit = {
    (0 to MAX).foreach(_ => http(Request.get(url = URL(Root / "text"))))
    ()
  }


  @Benchmark
  def benchmarkBase(): Unit = {
    (0 to MAX).foreach(_ => base(0))
    ()
  }

  @Benchmark
  def benchmarkBaseTotal(): Unit = {
    (0 to MAX).foreach(_ => baseTotal(0))
    ()
  }
}
