package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.service.Client.ClientResponse
import zio.Task

sealed trait IsResponse[-A] {
  def getBodyAsByteBuf(a: A): Task[ByteBuf]
  def getHeaders(a: A): Headers
  def getStatus(a: A): Status
}

object IsResponse {
  implicit object serverResponse extends IsResponse[Response] {
    def getBodyAsByteBuf(a: Response): Task[ByteBuf] = a.getBodyAsByteBuf
    def getHeaders(a: Response): Headers             = a.headers
    def getStatus(a: Response): Status               = a.status
  }

  implicit object clientResponse extends IsResponse[ClientResponse] {
    def getBodyAsByteBuf(a: ClientResponse): Task[ByteBuf] = a.getBodyAsByteBuf
    def getHeaders(a: ClientResponse): Headers             = a.headers
    def getStatus(a: ClientResponse): Status               = a.status
  }
}
