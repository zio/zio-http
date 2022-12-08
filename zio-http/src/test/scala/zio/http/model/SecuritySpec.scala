package zio.http.model

import zio.test.Assertion.equalTo
import zio.test.{ZIOSpecDefault, assert}

object SecuritySpec extends ZIOSpecDefault {
  def spec = suite("HttpError")(
    suite("security")(
      test("should sanitize HTML output, to protect agains XSS") {
        val error = HttpError.NotFound("<script>alert(\"xss\")</script>").message
        assert(error)(equalTo("The requested URI \"&lt;script&gt;alert(&#34;xss&#34;)&lt;/script&gt;\" was not found on this server\n".stripMargin))
      },
    ),
  )
}
