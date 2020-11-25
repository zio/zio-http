package zio.web.codec

import zio.Chunk
import zio.stream.{ ZSink, ZStream }
import zio.test.Assertion._
import zio.test._
import zio.web.schema.Schema

import scala.collection.SortedMap
import scala.util.Try

object ProtobufCodecSpec extends DefaultRunnableSpec {

  def spec = suite("ProtobufCodec Spec")(
    suite("Toplevel ProtobufCodec Spec")(
      testM("Should correctly encode integers") {
        assertM(encode(schemaBasicInt, BasicInt(150)).map(toHex))(
          equalTo("089601")
        )
      },
      testM("Should correctly encode strings") {
        assertM(encode(schemaBasicString, BasicString("testing")).map(toHex))(
          equalTo("0A0774657374696E67")
        )
      },
      testM("Should correctly encode floats") {
        assertM(encode(schemaBasicFloat, BasicFloat(0.001f)).map(toHex))(
          equalTo("0D6F12833A")
        )
      },
      testM("Should correctly encode doubles") {
        assertM(encode(schemaBasicDouble, BasicDouble(0.001)).map(toHex))(
          equalTo("09FCA9F1D24D62503F")
        )
      },
      testM("Should correctly encode embedded messages") {
        assertM(encode(schemaEmbedded, Embedded(BasicInt(150))).map(toHex))(
          equalTo("0A03089601")
        )
      },
      testM("Should correctly encode packed lists") {
        assertM(encode(schemaPackedList, PackedList(List(3, 270, 86942))).map(toHex))(
          equalTo("0A06038E029EA705")
        )
      },
      testM("Should correctly encode unpacked lists") {
        assertM(encode(schemaUnpackedList, UnpackedList(List("foo", "bar", "baz"))).map(toHex))(
          equalTo("0A03666F6F0A036261720A0362617A")
        )
      },
      testM("Should correctly encode records") {
        assertM(encode(schemaRecord, Record("Foo", 123)).map(toHex))(
          equalTo("0A03466F6F107B")
        )
      },
      testM("Should correctly encode enumerations") {
        assertM(encode(schemaEnumeration, Enumeration(IntValue(482))).map(toHex))(
          equalTo("10E203")
        )
      },
      testM("Should fill non-complete messages with default values") {
        assertM(decode(schemaRecord, "107B"))(
          equalTo(Chunk(Record("", 123)))
        )
      },
      testM("Should encode and decode successfully") {
        assertM(encodeAndDecode(schema, message))(
          equalTo(Chunk(message))
        )
      },
      testM("Should encode and decode successfully PACKED") {
        assertM(encodeAndDecode(schemaPackedList, PackedList(List(3, 270, 86942))))(
          equalTo(Chunk(PackedList(List(3, 270, 86942))))
        )
      },
      testM("Should encode and decode successfully NON-PACKED") {
        assertM(encodeAndDecode(schemaUnpackedList, UnpackedList(List("foo", "bar", "baz"))))(
          equalTo(Chunk(UnpackedList(List("foo", "bar", "baz"))))
        )
      },
      testM("Should encode and decode successfully ENUMERATION") {
        assertM(encodeAndDecode(schemaEnumeration, Enumeration(BooleanValue(true))))(
          equalTo(Chunk(Enumeration(BooleanValue(true))))
        )
      }
    )
  )

  // some tests are based on https://developers.google.com/protocol-buffers/docs/encoding

  case class BasicInt(value: Int)

  val schemaBasicInt: Schema[BasicInt] = Schema.caseClassN(
    "value" -> Schema[Int]
  )(BasicInt, BasicInt.unapply)

  case class BasicString(value: String)

  val schemaBasicString: Schema[BasicString] = Schema.caseClassN(
    "value" -> Schema[String]
  )(BasicString, BasicString.unapply)

  case class BasicFloat(value: Float)

  val schemaBasicFloat: Schema[BasicFloat] = Schema.caseClassN(
    "value" -> Schema[Float]
  )(BasicFloat, BasicFloat.unapply)

  case class BasicDouble(value: Double)

  val schemaBasicDouble: Schema[BasicDouble] = Schema.caseClassN(
    "value" -> Schema[Double]
  )(BasicDouble, BasicDouble.unapply)

  case class Embedded(embedded: BasicInt)

  val schemaEmbedded: Schema[Embedded] = Schema.caseClassN(
    "embedded" -> schemaBasicInt
  )(Embedded, Embedded.unapply)

  case class PackedList(packed: List[Int])

  val schemaPackedList: Schema[PackedList] = Schema.caseClassN(
    "packed" -> Schema.list(Schema[Int])
  )(PackedList, PackedList.unapply)

  case class UnpackedList(items: List[String])

  val schemaUnpackedList: Schema[UnpackedList] = Schema.caseClassN(
    "unpacked" -> Schema.list(Schema[String])
  )(UnpackedList, UnpackedList.unapply)

  case class Record(name: String, value: Int)

  val schemaRecord: Schema[Record] = Schema.caseClassN(
    "name"  -> Schema[String],
    "value" -> Schema[Int]
  )(Record, Record.unapply)

  sealed trait OneOf
  case class StringValue(value: String)   extends OneOf
  case class IntValue(value: Int)         extends OneOf
  case class BooleanValue(value: Boolean) extends OneOf

  val schemaOneOf: Schema[OneOf] = Schema.Transform(
    Schema.enumeration(
      SortedMap(
        "string"  -> Schema[String],
        "int"     -> Schema[Int],
        "boolean" -> Schema[Boolean]
      )
    ),
    (value: SortedMap[String, _]) => {
      value
        .get("string")
        .map(v => Right(StringValue(v.asInstanceOf[String])))
        .orElse(value.get("int").map(v => Right(IntValue(v.asInstanceOf[Int]))))
        .orElse(value.get("boolean").map(v => Right(BooleanValue(v.asInstanceOf[Boolean]))))
        .getOrElse(Left("No value found"))
    }, {
      case StringValue(v)  => Right(SortedMap("string"  -> v))
      case IntValue(v)     => Right(SortedMap("int"     -> v))
      case BooleanValue(v) => Right(SortedMap("boolean" -> v))
    }
  )

  case class Enumeration(oneOf: OneOf)

  val schemaEnumeration: Schema[Enumeration] =
    Schema.caseClassN("value" -> schemaOneOf)(Enumeration, Enumeration.unapply)

  // TODO: Generators instead of SearchRequest
  case class SearchRequest(query: String, pageNumber: Int, resultPerPage: Int)

  val schema: Schema[SearchRequest] = Schema.caseClassN(
    "query"         -> Schema[String],
    "pageNumber"    -> Schema[Int],
    "resultPerPage" -> Schema[Int]
  )(SearchRequest, SearchRequest.unapply)

  val message: SearchRequest = SearchRequest("bitcoins", 1, 100)

  def toHex(chunk: Chunk[Byte]): String =
    chunk.toArray.map("%02X".format(_)).mkString

  def fromHex(hex: String): Chunk[Byte] =
    Try(hex.split("(?<=\\G.{2})").map(Integer.parseInt(_, 16).toByte))
      .map(Chunk.fromArray)
      .getOrElse(Chunk.empty)

  def encode[A](schema: Schema[A], input: A) =
    ZStream
      .succeed(input)
      .transduce(ProtobufCodec.encoder(schema))
      .run(ZSink.collectAll)

  def decode[A](schema: Schema[A], hex: String) =
    ZStream
      .fromChunk(fromHex(hex))
      .transduce(ProtobufCodec.decoder(schema))
      .run(ZSink.collectAll)

  def encodeAndDecode[A](schema: Schema[A], input: A) =
    ZStream
      .succeed(input)
      .transduce(ProtobufCodec.encoder(schema))
      .transduce(ProtobufCodec.decoder(schema))
      .run(ZSink.collectAll)
}
