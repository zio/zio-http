package zio.http.netty

import zio.http.model.Version

import io.netty.handler.codec.http.HttpVersion

object Versions {
  import Version._

  def convertToZIOToNetty(version: Version): HttpVersion = version match {
    case Http_1_0 => HttpVersion.HTTP_1_0
    case Http_1_1 => HttpVersion.HTTP_1_1
  }

}
