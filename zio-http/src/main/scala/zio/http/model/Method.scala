package zio.http.model

import zio.stacktracer.TracingImplicits.disableAutoTrace

sealed trait Method { self =>
  val name: String

  override def toString: String = name
}

object Method {

  def fromString(method: String): Method =
    method.toUpperCase match {
      case POST.name    => Method.POST
      case GET.name     => Method.GET
      case OPTIONS.name => Method.OPTIONS
      case HEAD.name    => Method.HEAD
      case PUT.name     => Method.PUT
      case PATCH.name   => Method.PATCH
      case DELETE.name  => Method.DELETE
      case TRACE.name   => Method.TRACE
      case CONNECT.name => Method.CONNECT
      case x            => Method.CUSTOM(x)
    }

  final case class CUSTOM(name: String) extends Method

  object OPTIONS extends Method { val name = "OPTIONS" }
  object GET     extends Method { val name = "GET"     }
  object HEAD    extends Method { val name = "HEAD"    }
  object POST    extends Method { val name = "POST"    }
  object PUT     extends Method { val name = "PUT"     }
  object PATCH   extends Method { val name = "PATCH"   }
  object DELETE  extends Method { val name = "DELETE"  }
  object TRACE   extends Method { val name = "TRACE"   }
  object CONNECT extends Method { val name = "CONNECT" }
}
