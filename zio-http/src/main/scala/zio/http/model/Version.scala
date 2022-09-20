package zio.http.model

import io.netty.handler.codec.http.HttpVersion
import zio.Unsafe
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait Version { self =>
  def isHttp1_0: Boolean = self == Version.Http_1_0

  def isHttp1_1: Boolean = self == Version.Http_1_1

  def toJava: HttpVersion = self match {
    case Version.Http_1_0 => HttpVersion.HTTP_1_0
    case Version.Http_1_1 => HttpVersion.HTTP_1_1
  }
}

object Version {
  val `HTTP/1.0`: Version = Http_1_0
  val `HTTP/1.1`: Version = Http_1_1

  object unsafe {
    def fromJava(version: HttpVersion)(implicit unsafe: Unsafe): Version =
      version match {
        case HttpVersion.HTTP_1_0 => Http_1_0
        case HttpVersion.HTTP_1_1 => Http_1_1
        case _                    => throw new IllegalArgumentException(s"Unsupported HTTP version: $version")
      }
  }

  case object Http_1_0 extends Version

  case object Http_1_1 extends Version
}
