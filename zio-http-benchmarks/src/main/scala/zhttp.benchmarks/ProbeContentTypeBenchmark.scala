package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http.MediaType

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ProbeContentTypeBenchmark {

  private val fileNames = List("abc.mp4", "def", "ghi.mp3", "jkl.js", "mno.html", "pqr.css", "stu.gif", "vwx.jpeg")

  @Benchmark
  def benchmarkApp(): Unit = {
    val rand = Random.nextInt(8)
    MediaType.probeContentType(fileNames(rand))
    ()
  }
}
