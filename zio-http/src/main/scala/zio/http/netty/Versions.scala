package zio.http.netty

import io.netty.handler.codec.http.HttpVersion
import zio.Unsafe
import zio.http.model.Version

object Versions {
  import Version._

  def convertToZIOToNetty(version: Version)(implicit unsafe: Unsafe): HttpVersion = version match {
    case Http_1_0          => HttpVersion.HTTP_1_0
    case Http_1_1          => HttpVersion.HTTP_1_1
    case Unsupported(text) => HttpVersion.valueOf(text) // This case should never realistically occur.
  }

}
