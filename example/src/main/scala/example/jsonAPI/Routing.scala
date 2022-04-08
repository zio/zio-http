package example.jsonAPI

import zhttp.http._
import zio.json._
import zio.{UIO, ZIO}

import java.util.UUID
import scala.util.{Success, Try}

object Routing {

  private val list = Http.collect[Request] { case Method.GET -> !! / "users" / "list" => UserRequest.list }

  private val delete = Http.collectZIO[Request] { case Method.DELETE -> !! / "user" / id =>
    Try(UUID.fromString(id)) match {
      case Success(uuid) => UIO(UserRequest.delete(uuid))
      case _             => ZIO.fail(HttpError.BadRequest("Invalid UUID"))
    }
  }

  private val create = Http.collectZIO[Request] { case req @ Method.PUT -> !! / "user" / "create" =>
    req.bodyAsString flatMap { body =>
      body
        .fromJson[UserRequest]
        .fold(
          err => ZIO.fail(HttpError.BadRequest(err)),
          {
            case req: UserRequest.CreateUser => UIO(req)
            case _                           => ZIO.fail(HttpError.BadRequest("Invalid request payload"))
          },
        )
    }
  }

  private val response: Http[Any, Nothing, UserResponse, Response] =
    Http.collect[UserResponse] {
      case res: UserResponse.UserNotFound => Response.json(res.widen.toJson).setStatus(Status.NotFound)
      case res                            => Response.json(res.toJson)
    }

  private val request = list ++ delete ++ create

  def codec: Middleware[Any, Throwable, UserRequest, UserResponse, Request, Response] = request \/ response
}
