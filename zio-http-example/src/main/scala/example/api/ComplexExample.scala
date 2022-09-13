package example.api

import example.api.Domain._
import zhttp.api._
import zhttp.api.openapi.OpenApiInterpreter
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio._
import zio.json.{uuid => _, _}
import zio.schema.{DeriveSchema, Schema}

import java.util.UUID

// TODO:
// - Customize response headers or status code
// - Flesh out Doc
// - Error Type
// - Client Improvements
// - Making OpenAPI Support more robust/customizable
// - Exposing the OpenAPI Docs on a port
// - Deleting fancy yet pointless api/handlers validation macro business and types
// - Crazy Optimizations
//   - Reducing allocations
//   - Array(1, "hello", true, false) -> (1, "hello", true, false)
//   - /users - handler
//   -       /id - handler
//   -          /posts - handler
//   -                /id - handler
//   - /talks/id/posts
//   - /talks/id
//   - /talks

object ComplexExample extends ZIOAppDefault {

  val allTalks: API[Option[String], Unit, List[Talk]] =
    API.get("talks").query(string("title").?).output[List[Talk]]

  val getTalk: API[UUID, Unit, Option[Talk]] =
    API.get("talks" / uuid).output[Option[Talk]]

  val createTalk: API[Unit, CreateTalk, Talk] =
    API.post("talks").input[CreateTalk].output[Talk]

  val deleteTalk =
    API.delete("talks" / uuid)

  val apis =
    getTalk ++ allTalks ++ deleteTalk ++ createTalk

  // HANDLERS
  val allTalksHandler: Handler[Talks, Throwable, Option[String], Unit, List[Talk]] =
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
    deleteTalk.handle { uuid =>
      Talks.delete(uuid)
    }

  val handlers =
    getTalkHandler ++ allTalksHandler ++ deleteTalkHandler ++ createTalkHandler

  val run =
    printSchema *>
      Server
        .start(8080, handlers)
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

object ExampleClient extends ZIOAppDefault {

  val clientRequest =
    ComplexExample.getTalk
      .call("http://localhost:8080")(UUID.fromString("ee6c1806-3160-43e9-a501-95e1e8fcd1c2"))

  val run =
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
    def log(msg: String): ZIO[Logger, Nothing, Unit] =
      ZIO.serviceWithZIO[Logger](_.log(msg))

    val live: ULayer[Logger] =
      ZLayer.succeed(LoggerLive())

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
    def all: ZIO[Talks, Throwable, List[Talk]] =
      ZIO.serviceWithZIO[Talks](_.all)

    def get(id: UUID): ZIO[Talks, Throwable, Option[Talk]] =
      ZIO.serviceWithZIO[Talks](_.get(id))

    def create(title: String, description: String, duration: Int): ZIO[Talks, Throwable, Talk] =
      ZIO.serviceWithZIO[Talks](_.create(title, description, duration))

    def delete(id: UUID): ZIO[Talks, Throwable, Unit] =
      ZIO.serviceWithZIO[Talks](_.delete(id))

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

    val live: ZLayer[Logger, Nothing, Talks] = ZLayer {
      for {
        logger <- ZIO.service[Logger]
        ref    <- Ref.make(exampleTalks)
      } yield TalksLive(logger, ref)
    }
  }
}
