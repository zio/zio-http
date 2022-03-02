package zhttp.http

import io.netty.buffer.ByteBuf
import zio.Task

sealed trait IsResponse[-A] {
  def bodyAsByteBuf(a: A): Task[ByteBuf]
  def headers(a: A): Headers
  def status(a: A): Status
}

object IsResponse {
  implicit object Response extends IsResponse[Response] {
    def bodyAsByteBuf(a: Response): Task[ByteBuf] = a.bodyAsByteBuf
    def headers(a: Response): Headers             = a.headers
    def status(a: Response): Status               = a.status
  }
}
