package benchmark

import org.openjdk.jmh.annotations._
import zio._
import zio.http._

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{ConnectException, URI}
import java.util.concurrent.TimeUnit

@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FormToQueryBenchmark {

  val forms: List[Form] = List.fill(1000)(
    Form.fromQueryParams(QueryParams.apply(Map(
        "from" -> Chunk("2021-01-01T00:00:00Z"),
        "to" -> Chunk("2021-01-02T00:00:00Z"),
        "limit" -> Chunk("100"),
        "offset" -> Chunk("0"),
        "sort" -> Chunk("asc"),
        "filter" -> Chunk("true")
    ))
  )
    )

    @Benchmark
    def fromToQueryBenchmark(): Unit = {
      forms.foreach(_.toQueryParams)
    }
}
