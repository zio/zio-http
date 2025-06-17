package zio.http.internal

import zio.http._
import zio.schema.Schema
import zio.test._

object QueryGettersSpec extends ZIOSpecDefault {

  final case class PhoneNumber private (value: String)

  object PhoneNumber {
    def fromString(s: String): Option[PhoneNumber] =
      if (s.forall(_.isDigit)) Some(PhoneNumber(s)) else None

    implicit val schema: Schema[PhoneNumber] =
      Schema[String].transformOrFail(
        s => fromString(s).toRight(s"Invalid phone number: $s"),
        p => Right(p.value)
      )
  }

  val spec = suite("QueryGettersSpec")(
    test("decode mandatory query param with transform schema") {
      val req = Request.get(URL.empty).addQueryParam("phone", "1234567890")
      assertTrue(
        req.query[PhoneNumber]("phone") == Right(PhoneNumber("1234567890")),
      )
    },
    test("decode optional query param with transform schema") {
      val req = Request.get(URL.empty).addQueryParam("phone", "1234567890")
      assertTrue(
        req.query[Option[PhoneNumber]]("phone") == Right(Some(PhoneNumber("1234567890"))),
      )
    },
    test("decode missing optional query param with transform schema") {
      val req = Request.get(URL.empty)
      assertTrue(
        req.query[Option[PhoneNumber]]("phone") == Right(None),
      )
    }
  )
}
