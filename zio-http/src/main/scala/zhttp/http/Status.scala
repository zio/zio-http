package zhttp.http

import io.netty.handler.codec.http.{HttpResponseStatus => JHttpResponseStatus}

sealed trait Status { self =>
  def toJHttpStatus: JHttpResponseStatus = self match {
    case Status.OK                         => JHttpResponseStatus.OK
    case Status.BAD_REQUEST                => JHttpResponseStatus.BAD_REQUEST
    case Status.UNAUTHORIZED               => JHttpResponseStatus.UNAUTHORIZED
    case Status.FORBIDDEN                  => JHttpResponseStatus.FORBIDDEN
    case Status.NOT_FOUND                  => JHttpResponseStatus.NOT_FOUND
    case Status.METHOD_NOT_ALLOWED         => JHttpResponseStatus.METHOD_NOT_ALLOWED
    case Status.REQUEST_TIMEOUT            => JHttpResponseStatus.REQUEST_TIMEOUT
    case Status.REQUEST_ENTITY_TOO_LARGE   => JHttpResponseStatus.REQUEST_ENTITY_TOO_LARGE
    case Status.INTERNAL_SERVER_ERROR      => JHttpResponseStatus.INTERNAL_SERVER_ERROR
    case Status.NOT_IMPLEMENTED            => JHttpResponseStatus.NOT_IMPLEMENTED
    case Status.HTTP_VERSION_NOT_SUPPORTED => JHttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED
    case Status.SERVICE_UNAVAILABLE        => JHttpResponseStatus.SERVICE_UNAVAILABLE
    case Status.UPGRADE_REQUIRED           => JHttpResponseStatus.UPGRADE_REQUIRED
    case Status.MOVED_PERMANENTLY          => JHttpResponseStatus.MOVED_PERMANENTLY
  }
}

object Status {
  case object OK                         extends Status
  case object BAD_REQUEST                extends Status
  case object UNAUTHORIZED               extends Status
  case object FORBIDDEN                  extends Status
  case object NOT_FOUND                  extends Status
  case object METHOD_NOT_ALLOWED         extends Status
  case object REQUEST_TIMEOUT            extends Status
  case object REQUEST_ENTITY_TOO_LARGE   extends Status
  case object INTERNAL_SERVER_ERROR      extends Status
  case object NOT_IMPLEMENTED            extends Status
  case object HTTP_VERSION_NOT_SUPPORTED extends Status
  case object SERVICE_UNAVAILABLE        extends Status
  case object UPGRADE_REQUIRED           extends Status
  case object MOVED_PERMANENTLY          extends Status

  def fromJHttpResponseStatus(jStatus: JHttpResponseStatus): Status = jStatus match {
    case JHttpResponseStatus.OK                         => Status.OK
    case JHttpResponseStatus.BAD_REQUEST                => Status.BAD_REQUEST
    case JHttpResponseStatus.UNAUTHORIZED               => Status.UNAUTHORIZED
    case JHttpResponseStatus.FORBIDDEN                  => Status.FORBIDDEN
    case JHttpResponseStatus.NOT_FOUND                  => Status.NOT_FOUND
    case JHttpResponseStatus.METHOD_NOT_ALLOWED         => Status.METHOD_NOT_ALLOWED
    case JHttpResponseStatus.MOVED_PERMANENTLY          => Status.MOVED_PERMANENTLY
    case JHttpResponseStatus.REQUEST_TIMEOUT            => Status.REQUEST_TIMEOUT
    case JHttpResponseStatus.REQUEST_ENTITY_TOO_LARGE   => Status.REQUEST_ENTITY_TOO_LARGE
    case JHttpResponseStatus.INTERNAL_SERVER_ERROR      => Status.INTERNAL_SERVER_ERROR
    case JHttpResponseStatus.NOT_IMPLEMENTED            => Status.NOT_IMPLEMENTED
    case JHttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED => Status.HTTP_VERSION_NOT_SUPPORTED
    case JHttpResponseStatus.SERVICE_UNAVAILABLE        => Status.SERVICE_UNAVAILABLE
    case JHttpResponseStatus.UPGRADE_REQUIRED           => Status.UPGRADE_REQUIRED
  }

}
