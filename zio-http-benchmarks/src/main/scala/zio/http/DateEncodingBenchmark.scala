package zio.http

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date
import java.util.concurrent.TimeUnit

import zio.http.internal.DateEncoding

import io.netty.handler.codec.DateFormatter
import org.openjdk.jmh.annotations._

@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class DateEncodingBenchmark {

  // RFC 1123 date format
  val validDateStrings = List(
    "Mon, 01 Jan 2024 00:00:00 GMT",
    "Tue, 02 Jan 2024 12:30:45 GMT",
    "Wed, 03 Jan 2024 23:59:59 GMT",
    "Thu, 04 Jan 2024 15:45:30 GMT",
    "Fri, 05 Jan 2024 08:20:10 GMT",
    "Sat, 06 Jan 2024 18:00:00 GMT",
    "Sun, 07 Jan 2024 06:15:25 GMT",
    "Mon, 08 Jan 2024 14:05:50 GMT",
    "Tue, 09 Jan 2024 22:10:15 GMT",
    "Wed, 10 Jan 2024 03:30:00 GMT",
    "Thu, 11 Jan 2524 11:45:35 GMT",
    "Fri, 12 Jan 2024 20:55:45 GMT",
    "Sat, 13 Jan 2024 09:25:20 GMT",
  )

  val invalidDateStrings = List(
    "Mon 01 Jan 2024 00:00:00",
    "Tue; 02 Jan 2024 12:30:45 GMT+01:00",
    "Wed, 03 Jan 2024 23:59:59 UTC",
    "Thu, 04 Jan 2024 15:45:30 GMT-05:00",
    "Fri, 05 Jan 2024 08:20:10 GMT+02",
    "Sat, 06 Jan 2024 18:00:00 GMT+02:30",
    "Sun, 07 Jan 2024 06:15:25 GMT+03",
    "Mon, 08 Jan 2024 14:05:50 GMT+04",
    "Tue, 09 Jan 2024 22:10:15 GMT+05",
    "Wed, 10 Jan 2024 03:30:00 GMT+06",
    "Thu, 11 Jan 2524 11:45:35 GMT+07",
    "Fri, 12 Jan 2024 20:55:45 GMT+08",
    "Sat, 13 Jan 2024 09:25:20 GMT+09",
    "Invalid Date String",
    "",
  )

  val zonedDates = List(
    ZonedDateTime.parse("2024-01-01T00:00:00Z"),
    ZonedDateTime.parse("2024-01-02T12:30:45Z"),
    ZonedDateTime.parse("2024-01-03T23:59:59Z"),
    ZonedDateTime.parse("2024-01-04T15:45:30Z"),
    ZonedDateTime.parse("2024-01-05T08:20:10Z"),
    ZonedDateTime.parse("2024-01-06T18:00:00Z"),
    ZonedDateTime.parse("2024-01-07T06:15:25Z"),
    ZonedDateTime.parse("2024-01-08T14:05:50Z"),
    ZonedDateTime.parse("2024-01-09T22:10:15Z"),
    ZonedDateTime.parse("2024-01-10T03:30:00Z"),
    ZonedDateTime.parse("2024-01-11T11:45:35Z"),
    ZonedDateTime.parse("2024-01-12T20:55:45Z"),
    ZonedDateTime.parse("2024-01-13T09:25:20Z"),
  )

  @Benchmark
  def zioHttpEncoding(): Unit =
    zonedDates.foreach(DateEncoding.encodeDate)

  @Benchmark
  def zioHttpDecoding(): Unit =
    validDateStrings.foreach(DateEncoding.decodeDate)

  @Benchmark
  def zioHttpDecodingInvalid(): Unit =
    invalidDateStrings.foreach(DateEncoding.decodeDate)

  @Benchmark
  def nettyEncoding(): Unit =
    zonedDates.foreach(date => DateFormatter.format(Date.from(date.toInstant)))

  @Benchmark
  def nettyDecoding(): Unit =
    validDateStrings.foreach(date =>
      Option(DateFormatter.parseHttpDate(date)).map(date => ZonedDateTime.ofInstant(date.toInstant, ZoneOffset.UTC)),
    )

  @Benchmark
  def nettyDecodingInvalid(): Unit =
    invalidDateStrings.foreach(date =>
      Option(DateFormatter.parseHttpDate(date)).map(date => ZonedDateTime.ofInstant(date.toInstant, ZoneOffset.UTC)),
    )

}
