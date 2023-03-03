package zio.http.codec

/**
 * A simple codec is either equal to a given value, or unconstrained within a
 * domain of values.
 */
sealed trait SimpleCodec[Input, Output] extends PartialFunction[Input, Output] {
  def isDefinedAt(input: Input): Boolean

  def decode(input: Input): Either[String, Output]

  def encode(output: Output): Input
}
object SimpleCodec {
  final case class Specified[A](value: A) extends SimpleCodec[A, Unit] {
    def apply(input: A): Unit =
      if (isDefinedAt(input)) () else throw new MatchError(s"Expected $value but found $input")

    def decode(input: A): Either[String, Unit] =
      if (isDefinedAt(input)) Right(()) else Left(s"Expected $value but found $input")

    def encode(output: Unit): A = value

    def isDefinedAt(input: A): Boolean = input == value
  }
  final case class Unspecified[A]()       extends SimpleCodec[A, A]    {
    def apply(input: A): A = input

    def decode(input: A): Either[String, A] =
      Right(input)

    def encode(output: A): A = output

    def isDefinedAt(input: A): Boolean = true
  }
}
