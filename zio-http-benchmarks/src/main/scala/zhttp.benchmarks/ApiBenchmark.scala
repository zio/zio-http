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
    Request(url = URL.fromString("http://localhost:8080/users/1/posts/2?query=cool").toOption.get)

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
}
