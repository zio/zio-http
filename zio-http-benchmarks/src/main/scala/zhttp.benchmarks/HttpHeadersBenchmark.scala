package zhttp.benchmarks

import io.netty.handler.codec.http.ReadOnlyHttpHeaders
import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpHeadersBenchmark {
  @Benchmark
  def benchmarkWriteApp(): Unit = {
    val _ = Headers.empty
      .addHeader("Content-Type", "application/json")
      .addHeader("Content-Length", "0")
      .addHeader("Accepts", "application/json")
      .withContentEncoding("utf-8")
      .removeHeader("Accepts")
    ()
  }

  @Benchmark
  def benchmarkReadHeaders(): Unit = {
    val headers = Headers(
      ("Content-Type", "application/json"),
      ("Content-Length", "0"),
      ("Accepts", "application/json"),
      ("Content-Encoding", "utf-8"),
    )

    val _ = headers.header("Content-Encoding")
    val _ = headers.header("Content-Type")
  }

  @Benchmark
  def benchmarkHttpHeaders(): Unit = {
    val headers = Headers.make(
      new ReadOnlyHttpHeaders(
        true,
        "Content-Type",
        "application/json",
        "Content-Length",
        "0",
        "Accepts",
        "application/json",
        "Content-Encoding",
        "utf-8",
      ),
    )

    val _ = headers.header("Content-Encoding")
    val _ = headers.header("Content-Type")
  }
}
