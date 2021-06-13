package zhttp.http

import scala.annotation.implicitNotFound
import scala.annotation.implicitAmbiguous

@implicitNotFound("content unavailable")
sealed trait HasContent[-A] {
  type Out >: A

  def content[R, E, A1 <: Out](request: Request[R, E, A1]): Content[R, E, Out]   =
    request match {
      case Request.Default(_, _, _, dContent) => dContent
      case _                                  => throw InvalidAccess("content", request)
    }
  def content[R, E, A1 <: Out](response: Response[R, E, A1]): Content[R, E, Out] =
    response match {
      case Response.Default(_, _, dContent) => dContent
      case _                                => throw InvalidAccess("content", response)
    }
  def status[R, E, A1 <: A](response: Response[R, E, A1]): Status                = response match {
    case Response.Default(status, _, _) => status
    case _                              => throw InvalidAccess("status", response)
  }
  def headers[R, E, A1 <: A](response: Response[R, E, A1]): List[Header]         = response match {
    case Response.Default(_, headers, _) => headers
    case _                               => throw InvalidAccess("headers", response)
  }
}

object HasContent {
  @implicitAmbiguous("content unavailable")
  implicit case object HasNothing extends HasContent[Opaque] {
    override type Out = Opaque
  }

  @implicitAmbiguous("content unavailable")
  implicit case object HasBuffered extends HasContent[Buffered] {
    override type Out = Buffered
  }

  @implicitAmbiguous("content unavailable")
  implicit case object HasComplete extends HasContent[Complete] {
    override type Out = Complete
  }
}
