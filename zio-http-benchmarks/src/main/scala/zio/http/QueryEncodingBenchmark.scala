package zio.http

import java.util.concurrent.TimeUnit

import zio._

import zio.http.netty.NettyQueryParamEncoding

import org.openjdk.jmh.annotations._

@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class QueryEncodingBenchmark {

  val queryParams = List.fill(1000)(
    QueryParams.apply(
      Map(
        "from"         -> Chunk("2021-01-01T00:00:00Z"),
        "to"           -> Chunk("2021-01-02T00:00:00Z"),
        "limit"        -> Chunk("100"),
        "offset"       -> Chunk("0"),
        "sort"         -> Chunk("asc"),
        "filter"       -> Chunk("true"),
        "würstchen"    -> Chunk("äöüß"),
        "multiple"     -> Chunk("value1", "value2", "value3"),
        "empty"        -> Chunk.empty,
        "equalsInside" -> Chunk("value=with=equals"),
        "specialChars" -> Chunk("!@#$%^&*()_+-=[]{}|;':\",.<>?/~`"),
        "randomUTF8"   -> Chunk("こんにちは", "你好", "안녕하세요", "مرحبا", "Привет"),
        // we want to optimize for typical query params, so we avoid very long values
        // "veryLongValue" -> Chunk("a" * 1000), // Very long value
      ),
    ),
  )

  val encodedParams = queryParams.map(_.encode)

  @Benchmark
  def zioHttpEncoding(): Unit =
    queryParams.foreach(_.encode)

    @Benchmark
  def zioHttpDecoding(): Unit =
    encodedParams.foreach(QueryParams.decode(_))

  @Benchmark
  def nettyEncoding(): Unit =
    queryParams.foreach(NettyQueryParamEncoding.encode("", _, Charsets.Utf8))

    @Benchmark
  def nettyDecoding(): Unit =
    encodedParams.foreach(NettyQueryParamEncoding.decode(_, Charsets.Utf8))

}
