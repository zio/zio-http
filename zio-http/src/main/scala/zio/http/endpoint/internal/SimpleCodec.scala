package zio.http.endpoint.internal

/**
 * A simple codec describes either any value (of a certain type), or a single
 * value of that type. When used for decoding, a simple codec that accepts any
 * value will always succeed; but a simple codec that only accepts a specific
 * value will fail on any other value. When used for encoding, a simple codec
 * that accepts any value can encode that value. Whereas, a simple codec that
 * accepts just one value will always encode that value.
 */
sealed trait SimpleCodec[A]
object SimpleCodec {
  final case class Constant[A](value: A) extends SimpleCodec[A]
  final case class AnyValue[A]()         extends SimpleCodec[A]
}
