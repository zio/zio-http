package zio.http.datastar

import zio._
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._

object ReadSignalsSpec extends ZIOSpecDefault {

  case class User(name: String, age: Int, email: String)
  object User {
    implicit val schema: Schema[User] = DeriveSchema.gen[User]
  }

  case class SimpleSignal(count: Int)
  object SimpleSignal {
    implicit val schema: Schema[SimpleSignal] = DeriveSchema.gen[SimpleSignal]
  }

  case class NestedData(user: User, active: Boolean)
  object NestedData {
    implicit val schema: Schema[NestedData] = DeriveSchema.gen[NestedData]
  }

  override def spec = suite("ReadSignalsSpec")(
    suite("readSignals with GET requests")(
      test("should read signals from datastar header with simple data") {
        val jsonData = """{"count":42}"""
        val request  = Request
          .get(URL.root / "test")
          .addHeader(Header.Custom("datastar", jsonData))

        for {
          result <- readSignals[SimpleSignal](request)
        } yield assertTrue(
          result.count == 42,
        )
      },
      test("should read signals from datastar header with complex data") {
        val jsonData = """{"name":"John Doe","age":30,"email":"john@example.com"}"""
        val request  = Request
          .get(URL.root / "test")
          .addHeader(Header.Custom("datastar", jsonData))

        for {
          result <- readSignals[User](request)
        } yield assertTrue(
          result.name == "John Doe",
          result.age == 30,
          result.email == "john@example.com",
        )
      },
      test("should read signals from datastar header with nested data") {
        val jsonData = """{"user":{"name":"Jane","age":25,"email":"jane@test.com"},"active":true}"""
        val request  = Request
          .get(URL.root / "test")
          .addHeader(Header.Custom("datastar", jsonData))

        for {
          result <- readSignals[NestedData](request)
        } yield assertTrue(
          result.user.name == "Jane",
          result.user.age == 25,
          result.user.email == "jane@test.com",
          result.active == true,
        )
      },
      test("should fail when datastar header is missing in GET request") {
        val request = Request.get(URL.root / "test")

        for {
          result <- readSignals[SimpleSignal](request).either
        } yield assertTrue(
          result.isLeft,
        )
      },
      test("should fail when datastar header contains invalid JSON") {
        val request = Request
          .get(URL.root / "test")
          .addHeader(Header.Custom("datastar", "{invalid json"))

        for {
          result <- readSignals[SimpleSignal](request).either
        } yield assertTrue(
          result.isLeft,
        )
      },
      test("should fail when JSON doesn't match schema") {
        val jsonData = """{"wrong":"field"}"""
        val request  = Request
          .get(URL.root / "test")
          .addHeader(Header.Custom("datastar", jsonData))

        for {
          result <- readSignals[SimpleSignal](request).either
        } yield assertTrue(
          result.isLeft,
        )
      },
    ),
    suite("readSignals with POST requests")(
      test("should read signals from POST body with simple data") {
        val jsonData = """{"count":100}"""
        val request  = Request
          .post(URL.root / "test", Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[SimpleSignal](request)
        } yield assertTrue(
          result.count == 100,
        )
      },
      test("should read signals from POST body with complex data") {
        val jsonData = """{"name":"Alice Smith","age":28,"email":"alice@example.com"}"""
        val request  = Request
          .post(URL.root / "test", Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[User](request)
        } yield assertTrue(
          result.name == "Alice Smith",
          result.age == 28,
          result.email == "alice@example.com",
        )
      },
      test("should read signals from POST body with nested data") {
        val jsonData = """{"user":{"name":"Bob","age":35,"email":"bob@test.com"},"active":false}"""
        val request  = Request
          .post(URL.root / "test", Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[NestedData](request)
        } yield assertTrue(
          result.user.name == "Bob",
          result.user.age == 35,
          result.user.email == "bob@test.com",
          result.active == false,
        )
      },
      test("should fail when POST body contains invalid JSON") {
        val request = Request
          .post(URL.root / "test", Body.fromString("{not valid json}"))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[SimpleSignal](request).either
        } yield assertTrue(
          result.isLeft,
        )
      },
      test("should fail when POST body JSON doesn't match schema") {
        val jsonData = """{"differentField":"value"}"""
        val request  = Request
          .post(URL.root / "test", Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[SimpleSignal](request).either
        } yield assertTrue(
          result.isLeft,
        )
      },
      test("should fail when POST body is empty") {
        val request = Request
          .post(URL.root / "test", Body.empty)
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[SimpleSignal](request).either
        } yield assertTrue(
          result.isLeft,
        )
      },
    ),
    suite("readSignals with PUT requests")(
      test("should read signals from PUT body") {
        val jsonData = """{"count":200}"""
        val request  = Request
          .put(URL.root / "test", Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[SimpleSignal](request)
        } yield assertTrue(
          result.count == 200,
        )
      },
    ),
    suite("readSignals with PATCH requests")(
      test("should read signals from PATCH body") {
        val jsonData = """{"name":"Patched User","age":40,"email":"patched@example.com"}"""
        val request  = Request
          .patch(URL.root / "test", Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[User](request)
        } yield assertTrue(
          result.name == "Patched User",
          result.age == 40,
          result.email == "patched@example.com",
        )
      },
    ),
    suite("readSignals with DELETE requests")(
      test("should read signals from DELETE body") {
        val jsonData = """{"count":0}"""
        val request  = Request
          .delete(URL.root / "test")
          .withBody(Body.fromString(jsonData))
          .addHeader(Header.ContentType(MediaType.application.json))

        for {
          result <- readSignals[SimpleSignal](request)
        } yield assertTrue(
          result.count == 0,
        )
      },
    ),
  )
}
