package zhttp.http

import io.netty.handler.codec.http.{HttpMethod => JHttpMethod}

sealed trait Method { self =>
  lazy val asJHttpMethod: JHttpMethod = Method.asJHttpMethod(self)
}

object Method {
  object OPTIONS extends Method
  object GET     extends Method
  object HEAD    extends Method
  object POST    extends Method
  object PUT     extends Method
  object PATCH   extends Method
  object DELETE  extends Method
  object TRACE   extends Method
  object CONNECT extends Method

  def fromJHttpMethod(method: JHttpMethod): Either[HttpError, Method] =
    method match {
      case JHttpMethod.OPTIONS => Right(OPTIONS)
      case JHttpMethod.GET     => Right(GET)
      case JHttpMethod.HEAD    => Right(HEAD)
      case JHttpMethod.POST    => Right(POST)
      case JHttpMethod.PUT     => Right(PUT)
      case JHttpMethod.PATCH   => Right(PATCH)
      case JHttpMethod.DELETE  => Right(DELETE)
      case JHttpMethod.TRACE   => Right(TRACE)
      case JHttpMethod.CONNECT => Right(CONNECT)
      case _                   => Left(HttpError.MethodNotAllowed("Method not Allowed"))
    }

  def asJHttpMethod(self: Method): JHttpMethod = self match {
    case OPTIONS => JHttpMethod.OPTIONS
    case GET     => JHttpMethod.GET
    case HEAD    => JHttpMethod.HEAD
    case POST    => JHttpMethod.POST
    case PUT     => JHttpMethod.PUT
    case PATCH   => JHttpMethod.PATCH
    case DELETE  => JHttpMethod.DELETE
    case TRACE   => JHttpMethod.TRACE
    case CONNECT => JHttpMethod.CONNECT
  }
}
