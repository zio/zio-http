package example.api.experiment2

final case class Request(
  path: List[String],
  headers: Map[String, String],
  queryParams: Map[String, String],
)

trait Zipper[A, B] {
  type Out
}

object Zipper {
  type Aux[A, B, Out0] = Zipper[A, B] { type Out = Out0 }

}

trait LowPriorityZipper {
  implicit def zipper[A, B]: Zipper.Aux[A, B, (A, B)] = new Zipper[A, B] {
    type Out = (A, B)
  }
}

sealed trait RequestCodec[A] extends Product with Serializable

object RequestCodec {}

object Parser {
  def parseRequest[A](request: Request, codec: RequestCodec[A]): A = ???
}
