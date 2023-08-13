package zio.http.codec.internal

import zio._
import zio.test._

import zio.stream.{ZSink, ZStream}

import zio.schema._
import zio.schema.annotation.validate
import zio.schema.validation.Validation

import zio.http.codec.HttpCodecError

object BodyCodecSpec extends ZIOSpecDefault {
  import BodyCodec._

  case class User(
    @validate(Validation.greaterThan(0))
    id: Int,
    @validate(Validation.minLength(2) && Validation.maxLength(64))
    name: String,
  )
  object User {
    val schema: Schema[User] = DeriveSchema.gen[User]
  }

  def spec = suite("BodyCodecSpec")(
    suite("validateZIO")(
      test("returns a valid entity") {
        val valid = User(12, "zio")

        for {
          actual <- validateZIO(User.schema)(valid)
        } yield assertTrue(valid == actual)
      } +
        test("fails with HttpCodecError for invalid entity") {
          val invalid   = User(-4, "z")
          val validated = BodyCodec.validateZIO(User.schema)(invalid)

          assertZIO(validated.exit)(Assertion.failsWithA[HttpCodecError.InvalidEntity])
        },
    ),
    suite("validateStream")(
      test("returns all valid entities") {
        val users  = Chunk(
          User(1, "Will"),
          User(2, "Ammon"),
        )
        val valids = ZStream.fromChunk(users)

        for {
          validatedUsers <- valids.via(validateStream(User.schema)).runCollect
        } yield assertTrue(validatedUsers == users)
      },
      test("fails with HttpCodecError for invalid entity") {
        val users   = Chunk(
          User(1, "Will"),
          User(-5, "Ammon"),
        )
        val invalid = ZStream.fromChunk(users)

        for {
          validatedUsers <- invalid.via(validateStream(User.schema)).runCollect.exit
        } yield assert(validatedUsers)(Assertion.failsWithA[HttpCodecError.InvalidEntity])
      },
    ),
  )
}
