package zhttp.http

import zhttp.service.Client.ClientResponse

trait HasStatus[-A] extends (A => Status) {
  def get(a: A): Status
  override def apply(a: A): Status = get(a)
}

object HasStatus {
  import scala.language.implicitConversions
  implicit def convertToStatus[A](a: A)(implicit ev: HasStatus[A]): Status = ev.get(a)

  implicit val response: HasStatus[Response]             = new HasStatus[Response] {
    override def get(a: Response): Status = a.status
  }
  implicit val clientResponse: HasStatus[ClientResponse] = new HasStatus[ClientResponse] {
    override def get(a: ClientResponse): Status = a.status
  }
}
