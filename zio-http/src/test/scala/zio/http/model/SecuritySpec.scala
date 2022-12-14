package zio.http.model

import zio.test.Assertion.equalTo
import zio.test.{Spec, ZIOSpecDefault, assert}

object SecuritySpec extends ZIOSpecDefault {
  def spec: Spec[Any, Nothing] = suite("HttpError")(
    suite("security")(
      test("should encode HTML output, to protect against XSS") {
        val error = HttpError.NotFound("<script>alert(\"xss\")</script>").message
        assert(error)(
          equalTo(
            "The requested URI \"&lt;script&gt;alert(&quot;xss&quot;)&lt;&#x2F;script&gt;\" was not found on this server\n",
          ),
        )
      },
    ),
  )
}
