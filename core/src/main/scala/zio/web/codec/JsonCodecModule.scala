package zio.web.codec

trait JsonCodecModule extends ScalaCodecModule { m =>
  import zio.Chunk

  sealed trait Json

  type Input = String

  type CodecError = String

  sealed trait Codec[A] { self =>
    final def <*>[B](that: Codec[B]): Codec[(A, B)] = self.zip(that)

    final def *>[B](a0: A): Codec[B] => Codec[B] = { that: Codec[B] =>
      self.zip(that).transform(_._2, b => (a0, b))
    }

    final def <*[B](b0: B): Codec[B] => Codec[A] = { that: Codec[B] =>
      self.zip(that).transform(_._1, a => (a, b0))
    }

    def ascribe[B](semantic: Semantic[A, B]): Codec[B] = Codec.Ascribe(self, semantic)

    def encode(a: A): Input = ???

    def decode(i: Input): Either[CodecError, A] = ???

    final def transform[B](to: A => B, from: B => A): Codec[B] =
      Codec.Transform[A, B](self, a => Right(to(a)), b => Right(from(b)))

    final def transformError[B](to: A => Either[CodecError, B], from: B => Either[CodecError, A]): Codec[B] =
      Codec.Transform(self, to, from)

    final def zip[B](that: Codec[B]): Codec[(A, B)] = Codec.Zip(self, that)
  }

  object Codec {
    case object Null                                                           extends Codec[Unit]
    case object Bool                                                           extends Codec[Boolean]
    case object Str                                                            extends Codec[String]
    case object Num                                                            extends Codec[BigDecimal]
    sealed case class Obj(structure: Map[String, Codec[_]])                    extends Codec[Map[String, _]]
    sealed case class Arr[A](structure: Codec[A])                              extends Codec[Chunk[A]]
    sealed case class Ascribe[A, B](codec: Codec[A], semantic: Semantic[A, B]) extends Codec[B]
    sealed case class Zip[A, B](left: Codec[A], right: Codec[B])               extends Codec[(A, B)]
    sealed case class Transform[A, B](codec: Codec[A], to: A => Either[CodecError, B], from: B => Either[CodecError, A])
        extends Codec[B]
  }

  def unitCodec: Codec[Unit]     = Codec.Null
  def stringCodec: Codec[String] = Codec.Str
  def boolCodec: Codec[Boolean]  = Codec.Bool
  def shortCodec: Codec[Short]   = Codec.Num.transform(_.toShort, identity(_))
  def intCodec: Codec[Int]       = Codec.Num.transform(_.toInt, identity(_))
  def longCodec: Codec[Long]     = Codec.Num.transform(_.toLong, identity(_))
  def floatCodec: Codec[Float]   = Codec.Num.transform(_.toFloat, identity(_))
  def doubleCodec: Codec[Double] = Codec.Num.transform(_.toDouble, identity(_))
  def byteCodec: Codec[Byte]     = Codec.Num.transform(_.toByte, identity(_))
  def charCodec: Codec[Char]     = Codec.Num.transform(_.toChar, identity(_))

  def encode[A](codec: Codec[A], a: A): Input                        = codec.encode(a)
  def decode[A](codec: Codec[A], input: Input): Either[String, A]    = codec.decode(input)
  def zipCodec[A, B](left: Codec[A], right: Codec[B]): Codec[(A, B)] = left.zip(right)

  def transformCodecError[A, B](f: A => Either[CodecError, B], g: B => Either[CodecError, A]): Codec[A] => Codec[B] =
    codec => Codec.Transform(codec, f, g)
}
