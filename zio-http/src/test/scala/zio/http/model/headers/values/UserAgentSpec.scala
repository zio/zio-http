package zio.http.model.headers.values

import zio.test.{ZIOSpecDefault, assertTrue}

object UserAgentSpec extends ZIOSpecDefault {
  def spec = suite("UserAgent suite")(
    test("UserAgent should be parsed correctly") {
      val userAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36"
      println(UserAgent.toUserAgent(userAgent))
      assertTrue(
        UserAgent.toUserAgent(userAgent) == UserAgent.CompleteUserAgent(
          UserAgent.Product("Mozilla", Some("5.0")),
          Some(
            UserAgent.Comment(
              " (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
            ),
          ),
        ),
      )
    },
    test("UserAgent should be rendered correctly") {
      val userAgent = UserAgent.CompleteUserAgent(
        UserAgent.Product("Mozilla", Some("5.0")),
        Some(
          UserAgent.Comment(
            "Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
          ),
        ),
      )
      assertTrue(
        UserAgent.fromUserAgent(
          userAgent,
        ) == "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36)",
      )
    },
  )

}
