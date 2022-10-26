package zio.http.api.internal

sealed trait SimpleCodec[A]
object SimpleCodec {
  final case class Constant[A](value: A) extends SimpleCodec[A]
  final case class AnyValue[A]()         extends SimpleCodec[A]
}
