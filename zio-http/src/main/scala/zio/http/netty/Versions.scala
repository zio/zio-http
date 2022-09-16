package zio.http.netty

import io.netty.handler.codec.http.HttpVersion
import zio.http.Version
import zio._

object Versions {
  import Version._
  def make(version: HttpVersion)(implicit unsafe: Unsafe): Version =
    version match {
      case HttpVersion.HTTP_1_0 => Http_1_0
      case HttpVersion.HTTP_1_1 => Http_1_1
      case _                    => throw new IllegalArgumentException(s"Unsupported HTTP version: $version")
    }

  def make(version: Version): HttpVersion = version match {
    case Http_1_0 => HttpVersion.HTTP_1_0
    case Http_1_1 => HttpVersion.HTTP_1_1
  }

}
