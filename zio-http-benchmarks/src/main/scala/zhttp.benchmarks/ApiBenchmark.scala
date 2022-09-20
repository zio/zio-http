package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http._
import zio.http.model.Method
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.{Scope => _, _}

import java.util.concurrent.TimeUnit

final case class ExampleData(userId: Int, postId: Int, query: String)

object ExampleData {
  implicit val schema: Schema[ExampleData] =
    DeriveSchema.gen[ExampleData]

  implicit val jsonCodec: JsonCodec[ExampleData] =
    DeriveJsonCodec.gen[ExampleData]
}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ApiBenchmark {

  private val REPEAT_N = 1_000

  import zio.http.api._

  // # Small Data Request

  // API DSL
  val usersPosts =
    API
      .get(In.literal("users") / In.int / In.literal("posts") / In.int)
      .in(In.query("query"))
      .out[ExampleData]

  val handledUsersPosts =
    usersPosts.handle { case (userId, postId, limit) =>
      ZIO.succeed(ExampleData(userId, postId, limit))
    }

  val apiHttpApp = handledUsersPosts.toHttpApp

  // Collect DSL
  val collectHttpApp = Http.collectZIO[Request] { //
    case req @ Method.GET -> !! / "users" / userId / "posts" / postId =>
      val userIdInt = userId.toInt
      val postIdInt = postId.toInt
      val query     = req.url.queryParams.get("query").flatMap(_.headOption).get
      ZIO.succeed(
        Response.json(
          ExampleData(userIdInt, postIdInt, query).toJson,
        ),
      )
  }

  val request: Request =
    requestFromString("http://localhost:8080/users/1/posts/2?query=cool")

  def unsafeRun[E, A](zio: ZIO[Any, E, A]): Unit = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(zio.unit)
      .getOrThrowFiberFailure()
  }

  @Benchmark
  def benchmarkSmallDataZioApi(): Unit =
    unsafeRun {
      apiHttpApp(request).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkSmallDataZioCollect(): Unit =
    unsafeRun {
      collectHttpApp(request).repeatN(REPEAT_N)
    }

  // # Large Path

  // API DSL
  import In._

  val largePathHttpApp = API
    .get(In.literal("first") / int / "second" / int / "third" / int / "fourth" / int / "fifth" / int / "sixth" / int)
    .handle { _ =>
      ZIO.unit
    }
    .toHttpApp

  val largePathRequest: Request =
    requestFromString("http://localhost:8080/first/1/second/2/third/3/fourth/4/fifth/5/sixth/6")

  // Collect DSL

  val largePathCollectHttpApp = Http.collectZIO[Request] { //
    case Method.GET -> !! / "first" / id1 / "second" / id2 / "third" / id3 / "fourth" / id4 / "fifth" / id5 / "sixth" / id6 =>
      val _ = id1.toInt
      val _ = id2.toInt
      val _ = id3.toInt
      val _ = id4.toInt
      val _ = id5.toInt
      val _ = id6.toInt
      ZIO.unit
  }

  @Benchmark
  def benchmarkLargePathZioApi(): Unit =
    unsafeRun {
      largePathHttpApp(largePathRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkLargePathZioCollect(): Unit =
    unsafeRun {
      largePathCollectHttpApp(largePathRequest).repeatN(REPEAT_N)
    }

  private def requestFromString(url: String): Request =
    Request(url = URL.fromString(url).toOption.get)

}
