package zio.web.codec

import com.github.ghik.silencer.silent
import zio.{ Chunk, ZIO }
import zio.schema._
import zio.stream.{ ZStream, ZTransducer }

@silent("never used")
object JsonCodec extends Codec {

  override def encoder[A](schema: Schema[A]): ZTransducer[Any, Nothing, A, Byte] = Encoder(schema).mapChunks(_.flatten)

  override def decoder[A](schema: Schema[A]): ZTransducer[Any, String, Byte, A] = ZTransducer.fromPush {
    case Some(values: Chunk[Byte]) =>
      ZStream
        .fromChunk(Chunk.single(values))
        .transduce(Decoder(schema))
        .runCollect
    case None => ZIO.succeed(Chunk.empty)
  }

  object Encoder {

    def apply[A](schema: Schema[A]): ZTransducer[Any, Nothing, A, Chunk[Byte]] =
      schema match {
        case Schema.Record(structure)            => record(structure)
        case Schema.Sequence(underlaying)        => sequence(underlaying)
        case Schema.Enumeration(structure)       => ZTransducer.fromFunction(_ => charSequenceToByteChunk("enum"))
        case Schema.Transform(underlaying, _, g) => transform(underlaying, g)
        case Schema.Primitive(standardType)      => primitive(standardType)
        case Schema.Tuple(left, right)           => ZTransducer.fromFunction(_ => charSequenceToByteChunk("tuple"))
        case Schema.Optional(underlaying)        => ZTransducer.fromFunction(_ => charSequenceToByteChunk("optional"))
      }

    private def record[A](structure: Map[String, Schema[_]]): ZTransducer[Any, Nothing, A, Chunk[Byte]] =
      ZTransducer.fromFunctionM { value: Map[String, _] =>
        ZStream
          .fromChunk(Chunk.fromIterable(value.toSeq))
          .transduce(recordField(structure))
          .intersperse(OBJ_OPEN, OBJ_SEP, OBJ_CLOSE)
          .runCollect
      }.mapChunks(_.flatten)
        .asInstanceOf[ZTransducer[Any, Nothing, A, Chunk[Byte]]]

    private def recordField[A](structure: Map[String, Schema[_]]): ZTransducer[Any, Nothing, (String, A), Chunk[Byte]] =
      ZTransducer
        .fromFunctionM[Any, Nothing, (String, A), Chunk[Byte]] {
          case (field, a) =>
            val schema = structure(field).asInstanceOf[Schema[A]]
            val key    = ZStream.fromChunk(QUOTE ++ charSequenceToByteChunk(field) ++ QUOTE ++ OBJ_ASS)
            val value  = ZStream.fromChunk(Chunk.single(a)).transduce(Encoder(schema).mapChunks(_.flatten))

            (key ++ value).runCollect
        }

    private def sequence[A](schema: Schema[A]): ZTransducer[Any, Nothing, Chunk[A], Chunk[Byte]] =
      ZTransducer.fromFunctionM { values: Chunk[A] =>
        ZStream
          .fromChunk(values)
          .transduce(Encoder(schema))
          .intersperse(SEQ_OPEN, SEQ_SEP, SEQ_CLOSE)
          .runCollect
      }.mapChunks(_.flatten)

    private def transform[A, B](
      schema: Schema[A],
      g: B => Either[String, A]
    ): ZTransducer[Any, Nothing, A, Chunk[Byte]] =
      ZTransducer
        .fromFunction[B, A](value => g(value).toOption.get)
        .mapChunksM { value =>
          ZStream
            .fromChunk(value)
            .transduce(Encoder(schema))
            .runCollect
        }
        .asInstanceOf[ZTransducer[Any, Nothing, A, Chunk[Byte]]]

    private def primitive[A](standardType: StandardType[A]): ZTransducer[Any, Nothing, A, Chunk[Byte]] =
      standardType match {
        case StandardType.StringType =>
          ZTransducer.fromFunction[String, Chunk[Byte]](QUOTE ++ charSequenceToByteChunk(_) ++ QUOTE)
        case StandardType.ShortType  => standard[Short](_.toString)
        case StandardType.IntType    => standard[Int](_.toString)
        case StandardType.LongType   => standard[Long](_.toString)
        case StandardType.DoubleType => standard[Double](_.toString)
        case StandardType.FloatType  => standard[Float](_.toString)
        case _                       => ???
      }

    private lazy val nothing: ZTransducer[Any, Nothing, Any, Nothing] =
      ZTransducer.fromPush(_ => ZIO.succeed(Chunk.empty))

    private def standard[A](f: A => String): ZTransducer[Any, Nothing, A, Chunk[Byte]] =
      ZTransducer.fromFunction[A, Chunk[Byte]](v => charSequenceToByteChunk(f(v)))

    private def charSequenceToByteChunk(chars: CharSequence): Chunk[Byte] = {
      val bytes: Seq[Byte] = for (i <- 0 until chars.length) yield chars.charAt(i).toByte
      Chunk.fromIterable(bytes)
    }

    private lazy val QUOTE     = Chunk.single('"'.toByte)
    private lazy val SEQ_OPEN  = Chunk.single('['.toByte)
    private lazy val SEQ_SEP   = Chunk.single(','.toByte)
    private lazy val SEQ_CLOSE = Chunk.single(']'.toByte)
    private lazy val OBJ_OPEN  = Chunk.single('{'.toByte)
    private lazy val OBJ_SEP   = Chunk.single(','.toByte)
    private lazy val OBJ_ASS   = Chunk.single(':'.toByte)
    private lazy val OBJ_CLOSE = Chunk.single('}'.toByte)
  }

  object Decoder {

    def apply[A](schema: Schema[A]): ZTransducer[Any, String, Chunk[Byte], A] =
      trimWhitespace >>> (schema match {
        case Schema.Record(structure)            => record(structure)
        case Schema.Sequence(underlaying)        => sequence(underlaying)
        case Schema.Enumeration(structure)       => ZTransducer.fromEffect(ZIO.fail("enum"))
        case Schema.Transform(underlaying, f, _) => transform(underlaying, f)
        case Schema.Primitive(standardType)      => primitive(standardType)
        case Schema.Tuple(left, right)           => ZTransducer.fromEffect(ZIO.fail("tuple"))
        case Schema.Optional(underlaying)        => ZTransducer.fromEffect(ZIO.fail("optional"))
      }).asInstanceOf[ZTransducer[Any, String, Chunk[Byte], A]]

    private def record[A](structure: Map[String, Schema[_]]): ZTransducer[Any, String, Chunk[Byte], Map[String, _]] =
      peelCurlyBraces >>> trimWhitespace >>> ZTransducer.fromFunctionM[Any, String, Chunk[Byte], Map[String, _]] {
        case values =>
          ZStream
            .fromChunk(values)
            .transduce(ZTransducer.splitOnChunk(Chunk.single(OBJ_SEP)) >>> trimWhitespace)
            .flatMap { chunks =>
              val (left, right) = chunks.splitWhere(_ == OBJ_ASS)

              val key   = ZStream(left).transduce(trimWhitespace >>> trimQuotes).map(v => new String(v.toArray))
              val value = ZStream(right.drop(1)).transduce(trimWhitespace)

              key.zip(value).flatMap {
                case (field, chunk) =>
                  structure.get(field) match {
                    case Some(schema) =>
                      ZStream(chunk)
                        .transduce(Decoder(schema.asInstanceOf[Schema[Any]]))
                        .map(value => (field, value))
                    case None => ZStream.empty
                  }
              }
            }
            .runCollect
            .flatMap { fields =>
              val result = fields.toSeq.toMap
              ZIO.fromEither(Either.cond(result.keys.toSet == structure.keys.toSet, result, "Could not decode record"))
            }
      }

    private def primitive[A](standardType: StandardType[A]): ZTransducer[Any, String, Chunk[Byte], A] =
      standardType match {
        case StandardType.StringType =>
          matchBytes {
            case QUOTE +: value :+ QUOTE => Right(new String(value.toArray))
            case _                       => Left("Could not decode string")
          }
        case StandardType.IntType    => matchString(_.toIntOption, "int")
        case StandardType.ShortType  => matchString(_.toShortOption, "short")
        case StandardType.LongType   => matchString(_.toLongOption, "long")
        case StandardType.DoubleType => matchString(_.toDoubleOption, "double")
        case StandardType.FloatType  => matchString(_.toFloatOption, "float")
        case _                       => ???
      }

    private def matchBytes[A](f: Chunk[Byte] => Either[String, A]): ZTransducer[Any, String, Chunk[Byte], A] =
      ZTransducer.fromFunctionM[Any, String, Chunk[Byte], A] { v =>
        ZIO.fromEither(f(v))
      }

    private def matchString[A](f: String => Option[A], typeName: String): ZTransducer[Any, String, Chunk[Byte], A] =
      matchBytes(v => f(new String(v.toArray)).toRight(s"Could not decode $typeName"))

    private def sequence[A](schema: Schema[A]): ZTransducer[Any, String, Chunk[Byte], Chunk[A]] =
      peelSquareBraces >>> ZTransducer.fromFunctionM[Any, String, Chunk[Byte], Chunk[A]] {
        case values =>
          ZStream
            .fromChunk(values)
            .transduce(ZTransducer.splitOnChunk(Chunk.single(SEQ_SEP)))
            .flatMap { chunk =>
              ZStream.apply(chunk).transduce(Decoder(schema))
            }
            .runCollect
      }

    private def transform[A, B](
      schema: Schema[A],
      f: A => Either[String, B]
    ): ZTransducer[Any, String, Chunk[Byte], B] =
      ZTransducer.fromFunctionM[Any, String, Chunk[Byte], B] { value =>
        ZStream
          .apply(value)
          .transduce(Decoder(schema))
          .runHead
          .flatMap {
            case None        => ZIO.fail("Could not decode transform")
            case Some(value) => ZIO.fromEither(f(value))
          }
      }

    private lazy val trimWhitespace: ZTransducer[Any, Nothing, Chunk[Byte], Chunk[Byte]] =
      trimLeft >>> trimRight

    private lazy val trimLeft: ZTransducer[Any, Nothing, Chunk[Byte], Chunk[Byte]] =
      ZTransducer.fromFunction[Chunk[Byte], Chunk[Byte]](_.dropWhile(WHITESPACE.contains))

    private lazy val trimRight: ZTransducer[Any, Nothing, Chunk[Byte], Chunk[Byte]] =
      ZTransducer.fromFunction[Chunk[Byte], Chunk[Byte]](_.foldRight[Chunk[Byte]](Chunk.empty) {
        case (byte, acc) =>
          if (acc.isEmpty && (WHITESPACE contains byte)) Chunk.empty
          else byte +: acc
      })

    private def peelEnds(left: Byte, right: Byte, strict: Boolean): ZTransducer[Any, String, Chunk[Byte], Chunk[Byte]] =
      matchBytes {
        case left +: value :+ right => Right(value)
        case bytes =>
          if (strict) Left(s"Could not peel ${left.toChar} and ${right.toChar} from ends.")
          else Right(bytes)
      }

    private lazy val peelCurlyBraces  = peelEnds(OBJ_OPEN, OBJ_CLOSE, true)
    private lazy val peelSquareBraces = peelEnds(SEQ_OPEN, SEQ_CLOSE, true)
    private lazy val trimQuotes       = peelEnds(QUOTE, QUOTE, false)

    private lazy val QUOTE      = '"'.toByte
    private lazy val SEQ_OPEN   = '['.toByte
    private lazy val SEQ_SEP    = ','.toByte
    private lazy val SEQ_CLOSE  = ']'.toByte
    private lazy val OBJ_OPEN   = '{'.toByte
    private lazy val OBJ_SEP    = ','.toByte
    private lazy val OBJ_ASS    = ':'.toByte
    private lazy val OBJ_CLOSE  = '}'.toByte
    private lazy val SPACE      = ' '.toByte
    private lazy val CR         = '\r'.toByte
    private lazy val LF         = '\n'.toByte
    private lazy val WHITESPACE = Chunk(SPACE, CR, LF)
  }
}
