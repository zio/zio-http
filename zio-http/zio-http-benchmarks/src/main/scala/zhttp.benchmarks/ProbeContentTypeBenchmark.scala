package zio.benchmarks

import java.util.concurrent.TimeUnit

import scala.util.Random

import zio.http.Header.ContentType
import zio.http.MediaType

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ProbeContentTypeBenchmark {

  private val extensions = List("mp4", "def", "mp3", "js", "html", "css", "gif", "jpeg")
  private val header     = ContentType(MediaType.application.`json`)

  @Benchmark
  def benchmarkApp(): Unit = {
    val rand = Random.nextInt(8)
    MediaType.forFileExtension(extensions(rand))
    ()
  }

  @Benchmark
  def benchmarkParseMediaTypeSimple(): Unit = {
    MediaType.forContentType("application/json")
    ()
  }

  @Benchmark
  def benchmarkParseMediaTypeNotLowerCase(): Unit = {
    MediaType.forContentType("Application/json")
    ()
  }

  @Benchmark
  def benchmarkParseMediaTypeWithParams(): Unit = {
    MediaType.forContentType("application/json; charset=utf-8")
    ()
  }

  @Benchmark
  def benchmarkParseContentType(): Unit = {
    ContentType.parse("application/json; charset=utf-8")
    ()
  }

  @Benchmark
  def benchmarkRenderContentType(): Unit = {
    ContentType.render(header)
    ()
  }
}
