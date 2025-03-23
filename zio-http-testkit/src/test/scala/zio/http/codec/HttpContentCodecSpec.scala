package zio.http.codec

import java.nio.charset.StandardCharsets

import scala.collection.immutable.ListMap

import zio._
import zio.stream.{ZChannel, ZPipeline}
import zio.schema.codec.DecodeError.ReadError
import zio.schema.codec.JsonCodec.{JsonDecoder, JsonEncoder}
import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.internal.HeaderOps
import zio.http.template._
import scala.util.Try
import zio.test._
import zio.test.Assertion._

object HttpContentCodecSpec extends ZIOSpecDefault {
  case class User(name: String, age: Int)
  
  implicit val stringSchema: Schema[String] = Schema.primitive[String]
  implicit val intSchema: Schema[Int] = Schema.primitive[Int]
  implicit val userSchema: Schema[User] = DeriveSchema.gen[User]

  def spec = suite("HttpContentCodec")(
    test("should encode and decode string content as text/plain") {
      for {
        codec <- ZIO.succeed(HttpContentCodec.text.only[String])
        content = "Hello, World!"
        encoded = codec.encode(content).toOption.get
        request = Request(body = encoded, headers = Headers(Header.ContentType(MediaType.text.`plain`)))
        decoded <- codec.decodeRequest(request)
      } yield assertTrue(decoded == content)
    },
    test("should encode and decode integer content as text/plain") {
      for {
        codec <- ZIO.succeed(HttpContentCodec.text.only[Int])
        content = 42
        encoded = codec.encode(content).toOption.get
        request = Request(body = encoded, headers = Headers(Header.ContentType(MediaType.text.`plain`)))
        decoded <- codec.decodeRequest(request)
      } yield assertTrue(decoded == content)
    },
    test("should encode and decode case class content as JSON") {
      for {
        codec <- ZIO.succeed(HttpContentCodec.json.only[User])
        content = User("John", 30)
        encoded = codec.encode(content).toOption.get
        request = Request(body = encoded, headers = Headers(Header.ContentType(MediaType.application.`json`)))
        decoded <- codec.decodeRequest(request)
      } yield assertTrue(decoded == content)
    },
    test("should handle text/plain encoding for simple types") {
      for {
        codec <- ZIO.succeed(HttpContentCodec.text.only[String])
        value = "Hello World"
        encoded = codec.choices(MediaType.text.`plain`).codec(CodecConfig.defaultConfig).encode(value)
        decoded = codec.choices(MediaType.text.`plain`).codec(CodecConfig.defaultConfig).decode(encoded)
      } yield assertTrue(decoded.toOption.get == value)
    },
    test("should prefer text/plain over JSON when both are supported") {
      for {
        codec <- ZIO.succeed(HttpContentCodec.text.only[String] ++ HttpContentCodec.json.only[String])
        mediaTypes = Chunk(
          MediaTypeWithQFactor(MediaType.text.`plain`, Some(1.0)),
          MediaTypeWithQFactor(MediaType.application.`json`, Some(0.8))
        )
        (mediaType, _) = codec.chooseFirstOrDefault(mediaTypes)
      } yield assertTrue(mediaType == MediaType.text.`plain`)
    },
    test("should fail when trying to encode case class as text/plain") {
      val user = User("John", 30)
      val codec = HttpContentCodec.text.only[User]
      
      for {
        result <- ZIO.attempt(codec.encode(user)).exit
      } yield assert(result)(
        fails(
          hasField[Throwable, String]("message", _.getMessage, containsString("The requested content type is not supported for this data structure"))
        )
      )
    },
    test("should handle case class with text/plain accept header by using JSON") {
      val codec = HttpContentCodec.json.only[User]
      val content = User("John", 30)
      
      for {
        encoded <- ZIO.fromEither(codec.encode(content))
        request = Request(
          body = encoded,
          headers = Headers(
            Header.ContentType(MediaType.application.`json`),
            Header.Accept(MediaType.text.`plain`, MediaType.application.`json`)
          )
        )
        decoded <- codec.decodeRequest(request)
      } yield assert(decoded)(equalTo(content))
    },
    test("should return error for unsupported content type") {
      val contentCodec = HttpCodec.content[User](HttpContentCodec.text.only[User])
      val content = User("John", 30)
      val mediaTypes = Chunk(MediaTypeWithQFactor(MediaType.text.`plain`, Some(1.0)))
      
      for {
        result <- ZIO.attempt(contentCodec.encodeResponse(content, mediaTypes, CodecConfig.defaultConfig)).either
      } yield assert(result)(
        isLeft(
          hasField[Throwable, String]("message", _.getMessage, containsString("The requested content type is not supported for this data structure"))
        )
      )
    },
    test("should throw exception when text/plain encoding fails for complex types") {
      val codec = HttpContentCodec.json.only[User] ++ HttpContentCodec.text.only[User]
      val content = User("John", 30)
      val mediaTypes = Chunk(
        MediaTypeWithQFactor(MediaType.text.`plain`, Some(1.0)),
        MediaTypeWithQFactor(MediaType.application.`json`, Some(0.8))
      )
      
      val contentCodec = HttpCodec.content[User](codec)
      
      for {
        result <- ZIO.attempt(contentCodec.encodeResponse(content, mediaTypes, CodecConfig.defaultConfig)).either
      } yield assert(result)(
        isLeft(
          hasField[Throwable, String](
            "message", 
            _.getMessage, 
            containsString("The requested content type is not supported for this data structure")
          )
        )
      )
    },
    test("should successfully encode to JSON when JSON is the only requested media type") {
      val codec = HttpContentCodec.json.only[User] ++ HttpContentCodec.text.only[User]
      val content = User("John", 30)
      val mediaTypes = Chunk(
        MediaTypeWithQFactor(MediaType.application.`json`, Some(1.0))
      )
      
      val contentCodec = HttpCodec.content[User](codec)
      for {
        response <- ZIO.attempt(contentCodec.encodeResponse(content, mediaTypes, CodecConfig.defaultConfig))
      } yield assertTrue(response.headers.get(Header.ContentType).get.mediaType == MediaType.application.`json`)
    }
  )
}