package zhttp.http

import zhttp.socket.SocketApp

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self =>
  // todo: remove this once Response is migrated
  def getContentAsString[R1 <: R, E1 >: E](implicit ev: self.type <:< Response.HttpResponse[R1, E1]): Option[String] = {
    ev(self).content match {
      case HttpData.Text(data, _)       => Option(data)
      case HttpData.BinaryChunk(data)   => Option(data.map(_.toChar).mkString)
      case HttpData.BinaryByteBuf(data) => Option(data.toString(HTTP_CHARSET))
      case HttpData.BinaryStream(_)     => None
      case HttpData.Empty               => Option("")
    }
  }
}

object Response extends ResponseHelpers {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]
      with HasHeaders
      with HeadersHelpers

  final case class SocketResponse[-R, +E](socket: SocketApp[R, E] = SocketApp.empty) extends Response[R, E]
}
