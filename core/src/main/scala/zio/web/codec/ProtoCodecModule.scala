package zio.web.codec

trait ProtoCodecModule extends ScalaCodecModule { m =>
  import zio.Chunk

  type Input = Chunk[Byte]

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
    case object Unit                                                                extends Codec[Unit]
    case object Dbl                                                                 extends Codec[Double]
    case object Flt                                                                 extends Codec[Float]
    case object Int32                                                               extends Codec[Int]
    case object Int64                                                               extends Codec[Long]
    case object UInt32                                                              extends Codec[Int]
    case object UInt64                                                              extends Codec[Long]
    case object SInt32                                                              extends Codec[Int]
    case object SInt64                                                              extends Codec[Long]
    case object Fixed32                                                             extends Codec[Int]
    case object Fixed64                                                             extends Codec[Long]
    case object SFixed32                                                            extends Codec[Int]
    case object SFixed64                                                            extends Codec[Long]
    case object Bool                                                                extends Codec[Boolean]
    case object Str                                                                 extends Codec[String]
    case object Bytes                                                               extends Codec[Chunk[Byte]]
    sealed case class Nested(name: String, structure: Map[String, (Int, Codec[_])]) extends Codec[Map[String, _]]
    sealed case class Map[K, V](key: Codec[K], value: Codec[V])                     extends Codec[Map[K, V]]
    sealed case class Repeated[A](codec: Codec[A])                                  extends Codec[Chunk[A]]
    sealed case class Optional[A](codec: Codec[A])                                  extends Codec[Option[A]]
    sealed case class Ascribe[A, B](codec: Codec[A], semantic: Semantic[A, B])      extends Codec[B]
    sealed case class Zip[A, B](left: Codec[A], right: Codec[B])                    extends Codec[(A, B)]
    sealed case class Transform[A, B](codec: Codec[A], to: A => Either[CodecError, B], from: B => Either[CodecError, A])
        extends Codec[B]
  }

  def unitCodec: Codec[Unit]     = Codec.Unit
  def stringCodec: Codec[String] = Codec.Str
  def boolCodec: Codec[Boolean]  = Codec.Bool
  def shortCodec: Codec[Short]   = Codec.Int32.transform(_.toShort, _.toInt)
  def intCodec: Codec[Int]       = Codec.Int32.transform(_.toInt, identity(_))
  def longCodec: Codec[Long]     = Codec.Int64.transform(_.toLong, identity(_))
  def floatCodec: Codec[Float]   = Codec.Flt.transform(_.toFloat, identity(_))
  def doubleCodec: Codec[Double] = Codec.Dbl.transform(_.toDouble, identity(_))
  def byteCodec: Codec[Byte]     = Codec.Int32.transform(_.toByte, _.toInt)
  def charCodec: Codec[Char]     = Codec.Int32.transform(_.toChar, _.toInt)

  def encode[A](codec: Codec[A], a: A): Input                        = codec.encode(a)
  def decode[A](codec: Codec[A], input: Input): Either[String, A]    = codec.decode(input)
  def zipCodec[A, B](left: Codec[A], right: Codec[B]): Codec[(A, B)] = left.zip(right)

  def transformCodecError[A, B](f: A => Either[CodecError, B], g: B => Either[CodecError, A]): Codec[A] => Codec[B] =
    codec => Codec.Transform(codec, f, g)

}
