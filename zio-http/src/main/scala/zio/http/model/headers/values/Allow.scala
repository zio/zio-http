package zio.http.model.headers.values

import zio.Chunk
import zio.http.model.Method

import scala.annotation.tailrec

/**
 * The Allow header must be sent if the server responds with a 405 Method Not Allowed status code to indicate
 * which request methods can be used.
 */
sealed trait Allow {
  val raw: String
}

object Allow {

  case object OPTIONS extends Allow {
    override val raw: String = "OPTIONS"
  }
  case object GET extends Allow {
    override val raw: String = "GET"
  }
  case object HEAD extends Allow {
    override val raw: String = "HEAD"
  }
  case object POST extends Allow {
    override val raw: String = "POST"
  }
  case object PUT extends Allow {
    override val raw: String = "PUT"
  }
  case object PATCH extends Allow {
    override val raw: String = "PATCH"
  }
  case object DELETE extends Allow {
    override val raw: String = "DELETE"
  }
  case object TRACE extends Allow {
    override val raw: String = "TRACE"
  }
  case object CONNECT extends Allow {
    override val raw: String = "CONNECT"
  }
  case object InvalidAllowMethod extends Allow {
    override val raw: String = ""
  }
  final case class AllowMethods(methods: Chunk[Allow]) extends Allow {
    override val raw: String = methods.map(_.raw).mkString(", ")
  }

  private def parseAllowMethod(value: String): Allow =
    value.toUpperCase match {
      case POST.raw    => POST
      case GET.raw     => GET
      case OPTIONS.raw => OPTIONS
      case HEAD.raw    => HEAD
      case PUT.raw     => PUT
      case PATCH.raw   => PATCH
      case DELETE.raw  => DELETE
      case TRACE.raw   => TRACE
      case CONNECT.raw => CONNECT
      case _           => InvalidAllowMethod
    }

  def toAllow(value: String): Allow =
    if (value.isEmpty) AllowMethods(Chunk.empty)
    else AllowMethods(
      value
        .split(',')
        .foldLeft(Chunk.empty[Allow])((acc, value) => acc :+ parseAllowMethod(value.trim))
    )

  def fromAllow(allow: Allow): String = allow match {
    case AllowMethods(methods) => methods.map(fromAllow).filter(_.nonEmpty).mkString(", ")
    case method                => method.raw
  }

}
