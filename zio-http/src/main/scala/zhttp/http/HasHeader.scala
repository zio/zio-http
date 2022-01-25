package zhttp.http

import zhttp.service.Client.ClientResponse

trait HasHeader[-A] extends (A => Headers) {
  override def apply(a: A): Headers = get(a)

  def get(a: A): Headers
}

object HasHeader {
  import scala.language.implicitConversions
  implicit def convertToHeaders[A](a: A)(implicit ev: HasHeader[A]): Headers = ev.get(a)

  implicit val response: HasHeader[Response] = new HasHeader[Response] {
    override def get(a: Response): Headers = a.headers
  }

  implicit val clientResponse: HasHeader[ClientResponse] = new HasHeader[ClientResponse] {
    override def get(a: ClientResponse): Headers = a.headers
  }
}
