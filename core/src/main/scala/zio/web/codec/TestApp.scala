package zio.web.codec

import zio._
import zio.schema._
import zio.stream._

trait TestData {

  case class Person(name: String, age: Int)

  val personJson: String = """{"name": "John", "age": 42}"""

  val personSchema: Schema[Person] = Schema.caseClassN(
    "name" -> Schema[String],
    "age"  -> Schema[Int]
  )(Person.apply(_, _), Person.unapply)

  def encode[A](input: A, schema: Schema[A]) =
    for {
      _       <- console.putStrLn(":: Encoding test started")
      encoder = JsonCodec.encoder(schema)
      out     <- toJson(encoder, input)
      _       <- console.putStrLn(s"Result: $out")
      _       <- console.putStrLn(":: Encoding test ended")
    } yield ()

  def toJson[I](encoder: ZTransducer[Any, Nothing, I, Byte], input: I): ZIO[Any, Nothing, String] =
    ZStream.apply(input).transduce(encoder).runCollect.map(v => new String(v.toArray))

  def decode[A](input: String, schema: Schema[A]) =
    for {
      _       <- console.putStrLn(":: Decoding test started")
      decoder = JsonCodec.decoder(schema)
      out     <- fromJson(decoder, input)
      _       <- console.putStrLn(s"Result: $out")
      _       <- console.putStrLn(":: Decoding test ended")
    } yield ()

  def fromJson[O](decoder: ZTransducer[Any, String, Byte, O], input: String): ZIO[Any, String, O] =
    ZStream.fromIterable(input.getBytes).transduce(decoder).runHead.someOrFail("None decoded")
}

object TestApp extends App with TestData {

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program.exitCode

  lazy val program = decodePerson

  lazy val encodeList   = encode(List("foo", "bar", "baz"), Schema.list[String])
  lazy val encodePerson = encode(Person("John", 47), personSchema)

  lazy val decodeList   = decode("""    ["foo","bar","baz"]     """, Schema.list[String])
  lazy val decodePerson = decode("""{"name"   : "John"   ,   "age":47}""", personSchema)
}
