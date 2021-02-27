package zio-http.domain.codec

import zio.{UIO, ZIO}

case class Codec[-R, +E, -A0, +A1, -B0, +B1](
  decode: Codec.Convertor[R, E, A0, A1],
  encode: Codec.Convertor[R, E, B0, B1],
) { self =>

  def <<<[R1 <: R, E1 >: E, X, AA0 <: A0, AA1 >: A1, Y, BB0 <: B0, BB1 >: B1](
    other: Codec[R1, E1, X, AA0, BB1, Y],
  ): Codec[R1, E1, X, AA1, BB0, Y] =
    Codec(
      other.decode >>> self.decode,
      self.encode >>> other.encode,
    )

  def <>[R1 <: R, E1, AA0 <: A0, AA1 >: A1, BB0 <: B0, BB1 >: B1](
    other: Codec[R1, E1, AA0, AA1, BB0, BB1],
  ): Codec[R1, E1, AA0, AA1, BB0, BB1] =
    Codec(
      self.decode <> other.decode,
      self.encode <> other.encode,
    )

}

object Codec {

  case class Convertor[-R, +E, -A, +B](run: A => ZIO[R, E, B]) extends AnyVal {
    decoder =>

    def apply[A1 <: A](a: A1): ZIO[R, E, B] = run(a)

    def >>>[R1 <: R, E1 >: E, B1 >: B, C1](other: Convertor[R1, E1, B1, C1]): Convertor[R1, E1, A, C1] =
      Convertor(a => decoder(a) >>= other.run)

    def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Convertor[R1, E1, A1, B1]): Convertor[R1, E1, A1, B1] =
      Convertor(a => decoder(a) <> other(a))

    def &&&[R1 <: R, E1 >: E, X, Y](encoder: Convertor[R1, E1, X, Y]): Codec[R1, E1, A, B, X, Y] =
      Codec(decoder, encoder)
  }

  object Convertor {
    def identity[A]: Convertor[Any, Nothing, A, A] = Convertor(a => UIO(a))
  }

  def identity[A, B]: Codec[Any, Nothing, A, A, B, B] =
    Codec(Convertor.identity, Convertor.identity)

  def convertor[A]: MakeConvertor[A] = MakeConvertor(())

  final case class MakeConvertor[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: A => ZIO[R, E, B]): Convertor[R, E, A, B] = Convertor(pf)
  }
}
