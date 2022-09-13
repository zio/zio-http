package zhttp.api

import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio._
import zio.json.{uuid => _, _}
import zio.schema._
import zio.stream.{ZPipeline, ZStream}
import zio.test._

import java.util.UUID

object ServerClientSpec extends ZIOSpecDefault {
  // APIs

  // API[A]
  // - Server Interpreter = API => HttpApp
  //   - parseRequest: Request => Option[Input]
  //   - route, headers, queryParams

  val usersAPI =
    API.get("users").output[List[User]]

  val userAPI =
    API.get("users" / uuid).output[Option[User]]

  val countAPI =
    API.get("counter").output[Int]

  val incrementAPI: API[Unit, Int, Unit] =
    API.post("counter").input[Int]

  val streamAPI: API[Unit, ZStream[Any, Throwable, Byte], Unit] =
    API.post("stream").inputStream

  // Handlers

  val usersHandler =
    usersAPI.handle { _ =>
      ZIO.succeed {
        List(
          User(UUID.randomUUID(), "kit@email.com"),
          User(UUID.randomUUID(), "another@gmail.com"),
        )
      }
    }

  val userHandler =
    userAPI.handle { id =>
      ZIO.some(User(id, "kit@email.com"))
    }

  val countHandler =
    countAPI.handle { _ =>
      Counter.count
    }

  val incrementHandler =
    incrementAPI.handle { int =>
      Counter.increment(int)
    }

  val streamHandler =
    streamAPI.handle { stream =>
      (stream.tap(str => ZIO.debug(s"HI: $str")) >>> ZPipeline.utf8Decode)
        .tap(str => ZIO.debug(s"Received: $str"))
        .runCollect
        .flatMap { result =>
          ZIO.debug(s"DONE: $result") *> ZIO.succeed(result)
        }
    }

  val port = 9898
  val host = s"http://localhost:$port"

//  private val apis     = usersAPI ++ userAPI ++ countAPI ++ incrementAPI
  private val handlers = userHandler ++ usersHandler ++ countHandler ++ incrementHandler ++ streamHandler

  val serverLayer =
    ZLayer {
      Server.start(port, handlers).fork.unit
    }

  def spec =
    suite("ServerClientSpec")(
      test("get users") {
        for {
          users <- usersAPI.call(host)(())
        } yield assertTrue(users.length == 2)
      },
      test("get user") {
        for {
          _ <- ZIO.service[Unit]
          userId = UUID.randomUUID()
          user <- userAPI.call(host)(userId)
        } yield assertTrue(user.get.id == userId)
      },
      test("counter api") {
        for {
          count  <- countAPI.call(host)(())
          _      <- incrementAPI.call(host)(2) <&> incrementAPI.call(host)(4)
          count2 <- countAPI.call(host)(())
        } yield assertTrue(count == 0 && count2 == 6)
      },
      test("stream api") {
        for {
          _ <- streamAPI.call(host)(
            ZStream
              .unfoldZIO(100) { i =>
                if (i == 0) ZIO.none
                else ZIO.succeed(Some((i.toString, i - 1)))
              }
              .map(s => Chunk.fromArray(s.getBytes))
              .tap(_ => ZIO.debug("Sending chunk"))
              .flattenChunks,
          )
        } yield assertCompletes
      },
    ).provide(
      Counter.live,
      EventLoopGroup.auto(),
      ChannelFactory.auto,
      serverLayer,
    )

  // Example Service

  final case class Counter(ref: Ref[Int]) {
    def increment(amount: Int): ZIO[Any, Nothing, Unit] = ref.update(_ + amount)
    def count: ZIO[Any, Nothing, Int]                   = ref.get
  }

  object Counter {
    def increment(amount: Int): ZIO[Counter, Nothing, Unit] =
      ZIO.serviceWithZIO[Counter](_.increment(amount))

    val count: ZIO[Counter, Nothing, Int] =
      ZIO.serviceWithZIO[Counter](_.count)

    val live: ZLayer[Any, Nothing, Counter] =
      ZLayer(Ref.make(0)) >>> ZLayer.fromFunction(Counter.apply _)
  }

  // User

  final case class User(id: UUID, email: String)

  object User {
    implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen
    implicit val schema: Schema[User]   = DeriveSchema.gen
  }
}
