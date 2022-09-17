package zio.http.model

import zio.http._
import zio.test.Assertion.equalTo
import zio.test.{ZIOSpecDefault, assert}

object HttpErrorSpec extends ZIOSpecDefault {
  def spec = suite("HttpError")(
    suite("foldCause")(
      test("should fold the cause") {
        val error  = HttpError.InternalServerError(cause = Option(new Error("Internal server error")))
        val result = error.foldCause("")(cause => cause.getMessage)
        assert(result)(equalTo("Internal server error"))
      },
      test("should fold with no cause") {
        val error  = HttpError.NotFound(!!.encode)
        val result = error.foldCause("Page not found")(cause => cause.getMessage)
        assert(result)(equalTo("Page not found"))
      },
      test("should create custom error") {
        val error = HttpError.Custom(451, "Unavailable for legal reasons.")
        assert(error.status)(equalTo(Status.Custom(451)))
      },
    ),
  )
}
