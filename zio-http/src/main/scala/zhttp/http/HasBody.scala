package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.service.Client.ClientResponse
import zio.Task

trait HasBody[-A] extends (A => Task[ByteBuf]) {
  override def apply(a: A): Task[ByteBuf] = get(a)
  def get(a: A): Task[ByteBuf]
}

object HasBody {
  import scala.language.implicitConversions
  implicit def convertToHeaders[A](a: A)(implicit ev: HasBody[A]): Task[ByteBuf] = ev.get(a)

  implicit val response: HasBody[Response] = new HasBody[Response] {
    override def get(a: Response): Task[ByteBuf] = a.data.toByteBuf
  }

  implicit val clientResponse: HasBody[ClientResponse] = new HasBody[ClientResponse] {
    override def get(a: ClientResponse): Task[ByteBuf] = Task(a.buffer)
  }
}
