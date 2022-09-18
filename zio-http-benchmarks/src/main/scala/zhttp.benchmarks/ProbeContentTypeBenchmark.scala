package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http.model.MediaType

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ProbeContentTypeBenchmark {

  private val extensions = List("mp4", "def", "mp3", "js", "html", "css", "gif", "jpeg")

  @Benchmark
  def benchmarkApp(): Unit = {
    val rand = Random.nextInt(8)
    MediaType.forFileExtension(extensions(rand))
    ()
  }
}
