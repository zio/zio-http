package zio.web.codec

import java.nio.charset.StandardCharsets
import java.nio.{ ByteBuffer, ByteOrder }

import zio.stream.ZTransducer
import zio.web.schema._
import zio.{ Chunk, ZIO, ZManaged }

import scala.collection.immutable.SortedMap
import scala.collection.{ SortedMap => ISortedMap }

// TODO safe splitting of chunks
object ProtobufCodec extends Codec {
  override def encoder[A](schema: Schema[A]): ZTransducer[Any, Nothing, A, Byte] =
    ZTransducer(ZManaged.succeed(chunk => ZIO.succeed(Encoder.encodeChunk(schema, chunk))))

  override def decoder[A](schema: Schema[A]): ZTransducer[Any, String, Byte, A] =
    ZTransducer(ZManaged.succeed(chunk => ZIO.fromEither(Decoder.decodeChunk(schema, chunk))))

  sealed trait WireType {}

  case object VarInt                     extends WireType
  case object Bit64                      extends WireType
  case class LengthDelimited(width: Int) extends WireType
  case object StartGroup                 extends WireType
  case object EndGroup                   extends WireType
  case object Bit32                      extends WireType

  private def nestedFields(
    schema: Schema[_],
    nextFieldNumber: Int
  ): Option[SortedMap[Int, (String, Schema[_])]] = schema match {
    case _: Schema.Record      => None
    case _: Schema.Sequence[_] => None
    case Schema.Enumeration(structure) =>
      Some(flatFields(structure, nextFieldNumber)).map(
        _.map {
          case (fieldNumber, fieldAndSchema) =>
            val field = fieldAndSchema._1
            (fieldNumber,
             (field,
              Schema.Transform(fieldAndSchema._2.asInstanceOf[Schema[Any]],
                               (a: Any) => Right(SortedMap(field -> a)),
                               (b: SortedMap[String, Any]) => b.get(field).toRight("Missing value"))))
        }
      )
    case Schema.Transform(codec, f, g) =>
      nestedFields(codec, nextFieldNumber).map(_.map {
        case (fieldNumber, fieldAndSchema) =>
          (fieldNumber, (fieldAndSchema._1, Schema.Transform(fieldAndSchema._2.asInstanceOf[Schema[Any]], f, g)))
      })
    case _: Schema.Primitive[_] => None
    case _: Schema.Tuple[_, _]  => None
    case _: Schema.Optional[_]  => None
  }

  private def flatFields(
    structure: ISortedMap[String, Schema[_]],
    nextFieldNumber: Int = 1
  ): SortedMap[Int, (String, Schema[_])] = {
    val result = structure.toSeq
      .foldLeft((nextFieldNumber, SortedMap[Int, (String, Schema[_])]())) { (numAndMap, fieldAndSchema) =>
        nestedFields(fieldAndSchema._2, nextFieldNumber) match {
          case Some(fields) => (numAndMap._1 + fields.size, numAndMap._2 ++ fields)
          case None         => (numAndMap._1 + 1, numAndMap._2 + (numAndMap._1 -> fieldAndSchema))
        }
      }
      ._2
    System.out.println(result)
    result
  }

  object Encoder {

    def encodeChunk[A](schema: Schema[A], chunk: Option[Chunk[A]]): Chunk[Byte] =
      chunk.map(_.flatMap(encode(None, schema, _))).getOrElse(Chunk.empty)

    private def encode[A](fieldNumber: Option[Int], schema: Schema[A], value: A): Chunk[Byte] =
      (schema, value) match {
        case (Schema.Record(structure), v: ISortedMap[String, _])      => encodeRecord(fieldNumber, structure, v)
        case (Schema.Sequence(element), v: Chunk[_])                   => encodeSequence(fieldNumber, element, v)
        case (Schema.Enumeration(structure), v: ISortedMap[String, _]) => encodeEnumeration(structure, v)
        case (Schema.Transform(codec, _, g), _)                        => g(value).map(encode(fieldNumber, codec, _)).getOrElse(Chunk.empty)
        case (Schema.Primitive(standardType), v)                       => encodePrimitive(fieldNumber, standardType, v)
        case (Schema.Tuple(left, right), v @ (_, _))                   => encodeTuple(fieldNumber, left, right, v)
        case (Schema.Optional(codec), v: Option[_])                    => encodeOptional(fieldNumber, codec, v)
        case (_, _)                                                    => Chunk.empty
      }

    private def encodeRecord(
      fieldNumber: Option[Int],
      structure: ISortedMap[String, Schema[_]],
      data: ISortedMap[String, _]
    ): Chunk[Byte] =
      Chunk
        .fromIterable(flatFields(structure).toSeq.map {
          case (fieldNumber, (field, schema)) =>
            data
              .get(field)
              .map(value => encode(Some(fieldNumber), schema.asInstanceOf[Schema[Any]], value))
              .getOrElse(Chunk.empty)
        })
        .map(chunk => encodeKey(LengthDelimited(chunk.size), fieldNumber) ++ chunk)
        .flatten

    private def encodeSequence[A](
      fieldNumber: Option[Int],
      element: Schema[A],
      sequence: Chunk[A]
    ): Chunk[Byte] =
      if (canBePacked(element)) {
        val chunk = sequence.flatMap(value => encode(None, element, value))
        encodeKey(LengthDelimited(chunk.size), fieldNumber) ++ chunk
      } else {
        sequence.flatMap(value => encode(fieldNumber, element, value))
      }

    private def encodeEnumeration(
      structure: ISortedMap[String, Schema[_]],
      valueMap: ISortedMap[String, _]
    ): Chunk[Byte] =
      if (valueMap.isEmpty) {
        Chunk.empty
      } else {
        val (field, value) = valueMap.toSeq.head
        structure.zipWithIndex
          .find(v => v._1._1 == field)
          .map(v => encode(Some(v._2 + 1), v._1._2.asInstanceOf[Schema[Any]], value))
          .getOrElse(Chunk.empty)
      }

    @scala.annotation.tailrec
    private def encodePrimitive[A](
      fieldNumber: Option[Int],
      standardType: StandardType[A],
      value: A
    ): Chunk[Byte] =
      (standardType, value) match {
        case (StandardType.UnitType, _) =>
          Chunk.empty
        case (StandardType.StringType, str: String) =>
          val encoded = Chunk.fromArray(str.getBytes(StandardCharsets.UTF_8))
          encodeKey(LengthDelimited(encoded.size), fieldNumber) ++ encoded
        case (StandardType.BoolType, b: Boolean) =>
          encodeKey(VarInt, fieldNumber) ++ encodeVarInt(if (b) 1 else 0)
        case (StandardType.ShortType, v: Short) =>
          encodeKey(VarInt, fieldNumber) ++ encodeVarInt(v.toLong)
        case (StandardType.IntType, v: Int) =>
          encodeKey(VarInt, fieldNumber) ++ encodeVarInt(v)
        case (StandardType.LongType, v: Long) =>
          encodeKey(VarInt, fieldNumber) ++ encodeVarInt(v)
        case (StandardType.FloatType, v: Float) =>
          val byteBuffer = ByteBuffer.allocate(4)
          byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
          byteBuffer.putFloat(v)
          encodeKey(Bit32, fieldNumber) ++ Chunk.fromArray(byteBuffer.array)
        case (StandardType.DoubleType, v: Double) =>
          val byteBuffer = ByteBuffer.allocate(8)
          byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
          byteBuffer.putDouble(v)
          encodeKey(Bit64, fieldNumber) ++ Chunk.fromArray(byteBuffer.array)
        case (StandardType.ByteType, bytes: Chunk[Byte]) =>
          encodeKey(LengthDelimited(bytes.length), fieldNumber) ++ bytes
        case (StandardType.CharType, c: Char) =>
          encodePrimitive(fieldNumber, StandardType.StringType, c.toString)
        case (_, _) =>
          Chunk.empty
      }

    private def encodeTuple[A, B](
      fieldNumber: Option[Int],
      left: Schema[A],
      right: Schema[B],
      tuple: (A, B)
    ): Chunk[Byte] =
      encode(
        fieldNumber,
        Schema.record(SortedMap("left" -> left, "right" -> right)),
        SortedMap[String, Any]("left" -> tuple._1, "right" -> tuple._2)
      )

    private def encodeOptional[A](fieldNumber: Option[Int], schema: Schema[A], value: Option[A]): Chunk[Byte] =
      encode(
        fieldNumber,
        Schema.record(SortedMap("value" -> schema)),
        SortedMap("value" -> value)
      )

    private def encodeVarInt(value: Int): Chunk[Byte] =
      encodeVarInt(value.toLong)

    private def encodeVarInt(value: Long): Chunk[Byte] = {
      val base128    = value & 0x7F
      val higherBits = value >>> 7
      if (higherBits != 0x00) {
        (0x80 | base128).byteValue() +: encodeVarInt(higherBits)
      } else {
        Chunk(base128.byteValue())
      }
    }

    private def encodeKey(wireType: WireType, fieldNumber: Option[Int]): Chunk[Byte] =
      fieldNumber.map { num =>
        val encode = (base3: Int) => encodeVarInt(num << 3 | base3)
        wireType match {
          case VarInt                  => encode(0)
          case Bit64                   => encode(1)
          case LengthDelimited(length) => encode(2) ++ encodeVarInt(length)
          case StartGroup              => encode(3)
          case EndGroup                => encode(4)
          case Bit32                   => encode(5)
        }
      }.getOrElse(Chunk.empty)

    @scala.annotation.tailrec
    private def canBePacked(schema: Schema[_]): Boolean = schema match {
      case _: Schema.Record               => false
      case Schema.Sequence(element)       => canBePacked(element)
      case _: Schema.Enumeration          => false
      case Schema.Transform(codec, _, _)  => canBePacked(codec)
      case Schema.Primitive(standardType) => canBePacked(standardType)
      case _: Schema.Tuple[_, _]          => false
      case _: Schema.Optional[_]          => false
    }

    private def canBePacked(standardType: StandardType[_]): Boolean = standardType match {
      case StandardType.UnitType   => false
      case StandardType.StringType => false
      case StandardType.BoolType   => true
      case StandardType.ShortType  => true
      case StandardType.IntType    => true
      case StandardType.LongType   => true
      case StandardType.FloatType  => true
      case StandardType.DoubleType => true
      case StandardType.ByteType   => false
      case StandardType.CharType   => true
    }
  }

  object Decoder {

    trait Decoder[A] { self =>
      def run(chunk: Chunk[Byte], wireType: WireType): Either[String, (Chunk[Byte], A)]

      def map[B](f: A => B): Decoder[B] =
        (chunk: Chunk[Byte], wireType: WireType) =>
          self.run(chunk, wireType).map {
            case (remainder, value) => (remainder, f(value))
        }

      def flatMap[B](f: A => Decoder[B]): Decoder[B] =
        (chunk: Chunk[Byte], wireType: WireType) =>
          self.run(chunk, wireType).flatMap {
            case (remainder, value) => f(value).run(remainder, wireType)
        }
    }

    def decodeChunk[A](schema: Schema[A], chunk: Option[Chunk[Byte]]): Either[String, Chunk[A]] =
      chunk
        .map(bs => decoder(schema).run(bs, LengthDelimited(bs.size)))
        .map(_.map { case (_, value) => Chunk(value) })
        .getOrElse(Right(Chunk.empty))

    private def decoder[A](schema: Schema[A]): Decoder[A] =
      schema match {
        case Schema.Record(structure) => recordDecoder(structure).asInstanceOf[Decoder[A]]
        case Schema.Sequence(element) => sequenceDecoder(element).asInstanceOf[Decoder[A]]
        case Schema.Enumeration(_) =>
          (_, _) =>
            Left("oneof must be part of a message")
        case Schema.Transform(codec, f, _)  => transformDecoder(codec, f)
        case Schema.Primitive(standardType) => primitiveDecoder(standardType)
        case Schema.Tuple(left, right)      => tupleDecoder(left, right).asInstanceOf[Decoder[A]]
        case Schema.Optional(codec)         => optionalDecoder(codec).asInstanceOf[Decoder[A]]
      }

    private def recordDecoder(structure: ISortedMap[String, Schema[_]]): Decoder[SortedMap[String, _]] =
      recordLoopDecoder(flatFields(structure), defaultMap(structure))

    private def recordLoopDecoder(
      fields: SortedMap[Int, (String, Schema[_])],
      result: SortedMap[String, _]
    ): Decoder[SortedMap[String, _]] =
      (chunk, wireType) =>
        if (chunk.isEmpty) {
          Right((chunk, result))
        } else {
          recordLoopStepDecoder(fields, result).run(chunk, wireType)
      }

    private def recordLoopStepDecoder(
      fields: SortedMap[Int, (String, Schema[_])],
      result: SortedMap[String, _]
    ): Decoder[SortedMap[String, _]] =
      keyDecoder.flatMap {
        case (wireType, fieldNumber) =>
          val resultDecoder: Decoder[SortedMap[String, _]] =
            fields
              .get(fieldNumber)
              .map {
                case (field, schema) =>
                  fieldDecoder(wireType, schema).map {
                    case value: Seq[_] =>
                      val values = result.get(field).asInstanceOf[Option[Seq[_]]].map(_ ++ value).getOrElse(value)
                      result + (field -> values)
                    case value =>
                      result + (field -> value)
                  }
              }
              .getOrElse((chunk, _) => Right((chunk, result)))
          resultDecoder.flatMap(recordLoopDecoder(fields, _))
      }

    private def fieldDecoder[A](wireType: WireType, schema: Schema[A]): Decoder[A] =
      (chunk, _) => decoder(schema).run(chunk, wireType)

    private def sequenceDecoder[A](schema: Schema[A]): Decoder[Chunk[A]] =
      (chunk, wireType) =>
        wireType match {
          case LengthDelimited(length) =>
            sequenceLoopDecoder(schema, Chunk.empty).run(chunk.take(length), wireType).map {
              case (_, values) => (chunk.drop(length), values)
            }
          case _ =>
            Left("Invalid wire type")
      }

    private def sequenceLoopDecoder[A](schema: Schema[A], values: Chunk[A]): Decoder[Chunk[A]] =
      (chunk, wireType) =>
        if (chunk.isEmpty)
          Right((chunk, values))
        else
          decoder(schema)
            .run(chunk, wireType)
            .flatMap {
              case (remainder, value) =>
                sequenceLoopDecoder(schema, values :+ value).run(remainder, wireType)
          }

    private def transformDecoder[A, B](schema: Schema[B], f: B => Either[String, A]): Decoder[A] =
      decoder(schema).flatMap(a => (chunk, _) => f(a).map(b => (chunk, b)))

    private def primitiveDecoder[A](standardType: StandardType[_]): Decoder[A] =
      standardType match {
        case StandardType.UnitType   => ((chunk: Chunk[Byte]) => Right((chunk, ()))).asInstanceOf[Decoder[A]]
        case StandardType.StringType => stringDecoder.asInstanceOf[Decoder[A]]
        case StandardType.BoolType   => packedDecoder(VarInt, varIntDecoder).map(_ != 0).asInstanceOf[Decoder[A]]
        case StandardType.ShortType  => packedDecoder(VarInt, varIntDecoder).map(_.shortValue()).asInstanceOf[Decoder[A]]
        case StandardType.IntType    => packedDecoder(VarInt, varIntDecoder).map(_.intValue()).asInstanceOf[Decoder[A]]
        case StandardType.LongType   => packedDecoder(VarInt, varIntDecoder).asInstanceOf[Decoder[A]]
        case StandardType.FloatType  => floatDecoder.asInstanceOf[Decoder[A]]
        case StandardType.DoubleType => doubleDecoder.asInstanceOf[Decoder[A]]
        case StandardType.ByteType   => byteDecoder.asInstanceOf[Decoder[A]]
        case StandardType.CharType   => stringDecoder.map(_.charAt(0)).asInstanceOf[Decoder[A]]
      }

    private def tupleDecoder[A, B](left: Schema[A], right: Schema[B]): Decoder[(A, B)] =
      decoder(Schema.record(SortedMap("left" -> left, "right" -> right)))
        .flatMap(
          record =>
            (chunk, _) =>
              (record.get("left"), record.get("right")) match {
                case (Some(l), Some(r)) => Right((chunk, (l.asInstanceOf[A], r.asInstanceOf[B])))
                case _                  => Left("Failed decoding tuple")
          }
        )

    private def optionalDecoder[A](schema: Schema[_]): Decoder[Option[A]] =
      decoder(Schema.record(SortedMap("value" -> schema)))
        .map(record => record.get("value").asInstanceOf[Option[A]])

    private def stringDecoder: Decoder[String] = lengthDelimitedDecoder { length => (chunk, _) =>
      val (str, remainder) = chunk.splitAt(length)
      Right((remainder, new String(str.toArray, StandardCharsets.UTF_8)))
    }

    private def floatDecoder: Decoder[Float] =
      packedDecoder(
        Bit32,
        (chunk, _) => {
          val (float, remainder) = chunk.splitAt(4)
          val byteBuffer         = ByteBuffer.allocate(4)
          byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
          byteBuffer.put(float.toArray)
          Right((remainder, byteBuffer.getFloat()))
        }
      )

    private def doubleDecoder: Decoder[Double] =
      packedDecoder(
        Bit64,
        (chunk, _) => {
          val (double, remainder) = chunk.splitAt(8)
          val byteBuffer          = ByteBuffer.allocate(8)
          byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
          byteBuffer.put(double.toArray)
          Right((remainder, byteBuffer.getDouble))
        }
      )

    private def byteDecoder: Decoder[Byte] = lengthDelimitedDecoder { _ => (chunk, _) =>
      val (byte, remainder) = chunk.splitAt(1)
      Right((remainder, byte(0)))
    }

    private def lengthDelimitedDecoder[A](decoder: Int => Decoder[A]): Decoder[A] =
      (chunk, wireType) =>
        wireType match {
          case LengthDelimited(length) => decoder(length).run(chunk, wireType)
          case _                       => Left("Invalid wire type")
      }

    private def packedDecoder[A](decoderWireType: WireType, decoder: Decoder[A]): Decoder[A] =
      (chunk, wireType) =>
        wireType match {
          case LengthDelimited(_)               => decoder.run(chunk, wireType)
          case _ if decoderWireType == wireType => decoder.run(chunk, wireType)
          case _                                => Left("Invalid wire type")
      }

    private def keyDecoder: Decoder[(WireType, Int)] =
      varIntDecoder.flatMap { key => (chunk, wireType) =>
        val fieldNumber = (key >>> 3).toInt
        if (fieldNumber < 1) {
          Left("Failed decoding key")
        } else {
          (key & 0x07) match {
            case 0 => Right((chunk, (VarInt, fieldNumber)))
            case 1 => Right((chunk, (Bit64, fieldNumber)))
            case 2 => varIntDecoder.map(length => (LengthDelimited(length.toInt), fieldNumber)).run(chunk, wireType)
            case 3 => Right((chunk, (StartGroup, fieldNumber)))
            case 4 => Right((chunk, (EndGroup, fieldNumber)))
            case 5 => Right((chunk, (Bit32, fieldNumber)))
            case _ => Left("Failed decoding key")
          }
        }
      }

    private def varIntDecoder: Decoder[Long] =
      (chunk, _) =>
        if (chunk.isEmpty) {
          Left("Unexpected end of stream")
        } else {
          val length = chunk.indexWhere(octet => (octet.longValue() & 0x80) != 0x80) + 1
          val value  = chunk.take(length).foldRight(0L)((octet, v) => (v << 7) + (octet & 0x7F))
          Right((chunk.drop(length), value))
      }

    private def defaultMap(structure: ISortedMap[String, Schema[_]]): SortedMap[String, _] =
      structure.foldLeft(SortedMap[String, Any]())(
        (result, fieldAndSchema) =>
          defaultValue(fieldAndSchema._2).map(default => result + (fieldAndSchema._1 -> default)).getOrElse(result)
      )

    private def defaultValue(schema: Schema[_]): Option[Any] = schema match {
      case Schema.Record(structure)       => Some(defaultMap(structure))
      case Schema.Sequence(_)             => Some(Chunk())
      case _: Schema.Enumeration          => None
      case Schema.Transform(codec, f, _)  => defaultValue(codec).flatMap(f(_).toOption)
      case Schema.Primitive(standardType) => defaultValue(standardType)
      case Schema.Tuple(left, right)      => defaultValue(left).zip(defaultValue(right))
      case _: Schema.Optional[_]          => Some(None)
    }

    private def defaultValue(standardType: StandardType[_]): Option[Any] = standardType match {
      case StandardType.UnitType   => Some(())
      case StandardType.StringType => Some("")
      case StandardType.BoolType   => Some(false)
      case StandardType.ShortType  => Some(0)
      case StandardType.IntType    => Some(0)
      case StandardType.LongType   => Some(0L)
      case StandardType.FloatType  => Some(0.0f)
      case StandardType.DoubleType => Some(0.0)
      case StandardType.ByteType   => Some(Chunk.empty)
      case StandardType.CharType   => None
    }
  }
}
