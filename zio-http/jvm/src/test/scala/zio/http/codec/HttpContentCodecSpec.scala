package zio.http.codec

import zio._
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.endpoint.Endpoint

object HttpContentCodecSpec extends ZIOHttpSpec {

  final case class LoginForm(username: String, password: String)
  object LoginForm {
    implicit val schema: Schema[LoginForm] = DeriveSchema.gen[LoginForm]
  }

  final case class SingleField(value: String)
  object SingleField {
    implicit val schema: Schema[SingleField] = DeriveSchema.gen[SingleField]
  }

  private val formContentType = Header.ContentType(MediaType.application.`x-www-form-urlencoded`)

  override def spec = suite("HttpContentCodecSpec")(
    suite("form codec")(
      test("round-trip encode and decode a case class") {
        val codec = HttpContentCodec.form.only[LoginForm]
        val input = LoginForm("admin", "secret123")
        codec.encode(input) match {
          case Right(body) =>
            val request = Request.post("/test", body).addHeader(formContentType)
            for {
              decoded <- codec.decodeRequest(request)
            } yield assertTrue(decoded == input)
          case Left(err)   =>
            ZIO.succeed(assertTrue(false))
        }
      },
      test("decode url-encoded body") {
        val codec   = HttpContentCodec.form.only[LoginForm]
        val body    = Body
          .fromString("username=admin&password=secret123")
          .contentType(MediaType.application.`x-www-form-urlencoded`)
        val request = Request.post("/test", body).addHeader(formContentType)
        for {
          decoded <- codec.decodeRequest(request)
        } yield assertTrue(decoded == LoginForm("admin", "secret123"))
      },
      test("encode produces correct url-encoded format") {
        val codec = HttpContentCodec.form.only[LoginForm]
        val input = LoginForm("user1", "pass1")
        codec.encode(input) match {
          case Right(body) =>
            for {
              str <- body.asString
            } yield {
              val parts = str.split("&").toSet
              assertTrue(parts == Set("username=user1", "password=pass1"))
            }
          case Left(_)     =>
            ZIO.succeed(assertTrue(false))
        }
      },
      test("endpoint with form-encoded input") {
        val endpoint = Endpoint(Method.POST / "login")
          .inForm[LoginForm]
          .out[String]

        val routes = endpoint.implementHandler(Handler.fromFunction[LoginForm] { form =>
          s"Welcome ${form.username}"
        })

        val body    =
          Body.fromString("username=admin&password=secret").contentType(MediaType.application.`x-www-form-urlencoded`)
        val request = Request.post("/login", body).addHeader(formContentType)

        for {
          response <- routes.toRoutes.runZIO(request)
          result   <- response.body.asString
        } yield assertTrue(result == "\"Welcome admin\"")
      },
      test("decode single-field case class") {
        val codec   = HttpContentCodec.form.only[SingleField]
        val body    = Body.fromString("value=hello").contentType(MediaType.application.`x-www-form-urlencoded`)
        val request = Request.post("/test", body).addHeader(formContentType)
        for {
          decoded <- codec.decodeRequest(request)
        } yield assertTrue(decoded == SingleField("hello"))
      },
    ),
  )
}
