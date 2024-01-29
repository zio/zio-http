package zio.http.netty.benchmarks

import java.util.concurrent.TimeUnit

import zio.http.internal.{CaseMode, CharSequenceExtensions}
import zio.http.netty.model.Conversions

import io.netty.handler.codec.http.DefaultHttpHeaders
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
class UtilBenchmark {

  private val nettyHeaders =
    new DefaultHttpHeaders()
      .add("Content-Type", "application/json; charset=utf-8")
      .add("Content-Length", "100")
      .add("Content-Encoding", "gzip")
      .add("Accept", "application/json")
      .add("Accept-Encoding", "gzip, deflate, br")
      .add("Accept-Language", "en-US,en;q=0.9")
      .add("Connection", "keep-alive")
      .add("Host", "localhost:8080")
      .add("Origin", "http://localhost:8080")
      .add("Referer", "http://localhost:8080/")
      .add("Sec-Fetch-Dest", "empty")
      .add("Sec-Fetch-Mode", "cors")
      .add("Sec-Fetch-Site", "same-origin")
      .add(
        "User-Agent",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/88.0.4324.96 Chrome/88.0.4324.96 Safari/537.36",
      )

  private val headers = Conversions.headersFromNetty(nettyHeaders)

  @Benchmark
  def benchmarkEqualsInsensitive(): Unit = {
    CharSequenceExtensions.equals(
      "application/json; charset=utf-8",
      "Application/json; charset=utf-8",
      caseMode = CaseMode.Insensitive,
    )
    ()
  }

  @Benchmark
  // For comparison with benchmarkEqualsInsensitive
  def benchmarkEqualsInsensitiveJava(): Unit = {
    val _ = "application/json; charset=utf-8".equalsIgnoreCase("application/json; Charset=utf-8")
    ()
  }

  @Benchmark
  def benchmarkHeaderGetUnsafe(): Unit = {
    headers.getUnsafe("sec-fetch-site")
    ()
  }

  @Benchmark
  def benchmarkStatusToNetty(): Unit = {
    Conversions.statusToNetty(zio.http.Status.InternalServerError)
    ()
  }

}
