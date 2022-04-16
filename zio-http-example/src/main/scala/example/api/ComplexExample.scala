package example.api

import zhttp.http.HttpApp
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio._
import zhttp.api._
import zhttp.api.openapi.OpenApiInterpreter
import zio.json.{uuid => _, _}
import zio.schema.{DeriveSchema, Schema}
import Domain._

import java.util.UUID

// API DSL
// - Declarative
// - Introspectable description of your API
// - Haskell Servant (Types) — Refined Type
// - Ergonomics
// - Speed
// - Tapir -> ZHTTP
// - Endpoint4s
// - Description + HandlerLogic -> Server
// - Description + Arguments    -> Client
// - Description                -> OpenAPI Documentation

// TODO:
// - Customize response headers or status code
// - Flesh out Doc
// - Error Type
// - Client Improvements
// - Making OpenAPI Support more robust/customizable
// - Exposing the OpenAPI Docs on a port
// - Removing hard coded JsonCodec, rely fully on Schema
// - Deleting fancy yet pointless api/handlers validation macro business and types
// - Crazy Optimizations
//   - Reducing allocations
//   - Array(1, "hello", true, false) -> (1, "hello", true, false)
//   - /users - handler
//   -       /id - handler
//   -          /posts - handler
//   -                /id - handler
//   - /talks/id
//   - /talks/id

object ZIOWorld extends App {
  // APIs

  // GET /talks?title=my_talk

  // case req @ Methods.GET -> !! / "talks" -> req.queryParams =>

  val cool: HttpApp[Any, Nothing] =
    API
      .get("dogs" / string)
      .toHttp { string =>
        ZIO.succeed(string.length)
      }

  val allTalks: API[Option[String], Unit, List[Talk]] =
    API
      .get("talks")
      .query(string("title").?)
      .output[List[Talk]]

  val getTalk: API[UUID, Unit, Option[Talk]] =
    API
      .get("talks" / uuid)
      .output[Option[Talk]]

  val createTalk: API[Unit, CreateTalk, Talk] =
    API
      .post("talks")
      .input[CreateTalk]
      .output[Talk]

  val deleteTalk =
    API
      .delete("talks" / uuid)

  val apis =
    getTalk ++ allTalks ++ deleteTalk ++ createTalk

  // HANDLERS
  val allTalksHandler =
    allTalks.handle {
      case Some(filter) =>
        Talks.all.map(_.filter(_.title.toLowerCase.contains(filter.toLowerCase)))
      case _            =>
        Talks.all
    }

  val getTalkHandler =
    getTalk.handle { uuid =>
      Talks.get(uuid)
    }

  val createTalkHandler =
    createTalk.handle { case CreateTalk(title, description, duration) =>
      Talks.create(title, description, duration)
    }

  val deleteTalkHandler =
    Handler.make(deleteTalk) { case (uuid, _) =>
      Talks.delete(uuid)
    }

  val handlers =
    getTalkHandler ++ allTalksHandler ++ deleteTalkHandler ++ createTalkHandler

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    printSchema *>
      Server
        .start(8080, apis, handlers)
        .provideLayer(Logger.live >>> Talks.live)
        .exitCode

  lazy val printSchema =
    ZIO.debug(OpenApiInterpreter.generate(apis)("ZIO World", "API for ZIO World 2022 Talks"))

}

final case class CreateTalk(title: String, description: String, duration: Int)

object CreateTalk {
  implicit val codec: JsonCodec[CreateTalk] = DeriveJsonCodec.gen[CreateTalk]
  implicit val schema: Schema[CreateTalk]   = DeriveSchema.gen[CreateTalk]
}

object ExampleClient extends App {

  val clientRequest =
    ZIOWorld.getTalk
      .call("http://localhost:8080")(UUID.fromString("ee6c1806-3160-43e9-a501-95e1e8fcd1c2"))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    clientRequest.map(_.mkString("\n")).debug.provideLayer(EventLoopGroup.auto() ++ ChannelFactory.auto).exitCode
}

object Domain {
  object Talk {
    implicit val schema: Schema[Talk] = DeriveSchema.gen
  }

  final case class Talk(
    id: UUID,
    title: String,
    speaker: String,
    duration: Int,
  )

  trait Logger {
    def log(msg: String): UIO[Unit]
  }

  object Logger {
    def log(msg: String): ZIO[Has[Logger], Nothing, Unit] =
      ZIO.serviceWith[Logger](_.log(msg))

    val live: ULayer[Has[Logger]] =
      ZIO.succeed(LoggerLive()).toLayer[Logger]

    final case class LoggerLive() extends Logger {
      override def log(msg: String): UIO[Unit] = ZIO.succeed(println(msg))
    }
  }

  import java.util.UUID

  // A CRUD-dy Talks Service

  trait Talks {

    def all: Task[List[Talk]]

    def get(id: UUID): Task[Option[Talk]]

    def create(title: String, description: String, duration: Int): Task[Talk]

    def delete(id: UUID): Task[Unit]

  }

  object Talks {

    // Accessors
    def all: ZIO[Has[Talks], Throwable, List[Talk]] =
      ZIO.serviceWith[Talks](_.all)

    def get(id: UUID): ZIO[Has[Talks], Throwable, Option[Talk]] =
      ZIO.serviceWith[Talks](_.get(id))

    def create(title: String, description: String, duration: Int): ZIO[Has[Talks], Throwable, Talk] =
      ZIO.serviceWith[Talks](_.create(title, description, duration))

    def delete(id: UUID): ZIO[Has[Talks], Throwable, Unit] =
      ZIO.serviceWith[Talks](_.delete(id))

    final case class TalksLive(log: Logger, ref: Ref[Map[UUID, Talk]]) extends Talks {
      override def all: Task[List[Talk]] =
        log.log("GETTING ALL USERS") *>
          ref.get.map(_.values.toList)

      override def get(id: UUID): Task[Option[Talk]] =
        log.log(s"GETTING USER $id") *>
          ref.get.map(_.get(id))

      override def create(title: String, description: String, duration: Int): Task[Talk] = {
        val talk = Talk(UUID.randomUUID(), title, description, duration)
        ref.update(_ + (talk.id -> talk)).as(talk)
      }

      override def delete(id: UUID): Task[Unit] =
        ref.update(_ - id)
    }

    val kitTalk      = Talk(UUID.randomUUID(), "Intro to ZIO API", "Kit Langton", 10)
    val adamTalk     = Talk(UUID.randomUUID(), "Scopes! Scopes! Scopes!", "Adam Fraser", 10)
    val wiemTalk     = Talk(UUID.randomUUID(), "Book Your Spot and ZIO!", "Wiem Zine Elabidine", 10)
    val exampleTalks = List(kitTalk, adamTalk, wiemTalk).map(t => t.id -> t).toMap

    val live: ZLayer[Has[Logger], Nothing, Has[Talks]] = {
      for {
        logger <- ZIO.service[Logger]
        ref    <- Ref.make(exampleTalks)
      } yield TalksLive(logger, ref)
    }.toLayer[Talks]
  }
}
