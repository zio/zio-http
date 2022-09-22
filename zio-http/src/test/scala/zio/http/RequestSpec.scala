package zio.http

import zio.Scope
import zio.http.model._
import zio.test._

object RequestSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("Result")(
    test("`#default`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.POST,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.default(Method.POST, URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#delete`") {
      val expected = Request(
        Body.empty,
        Headers.empty,
        Method.DELETE,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.delete(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#get`") {
      val expected = Request(
        Body.empty,
        Headers.empty,
        Method.GET,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.get(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#options`") {
      val expected = Request(
        Body.empty,
        Headers.empty,
        Method.OPTIONS,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.options(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#patch`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.PATCH,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.patch(body, URL.empty)
      assertTrue(actual == expected)
    },
    test("`#post`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.POST,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.post(body, URL.empty)
      assertTrue(actual == expected)
    },
    test("`#put`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.PUT,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.put(body, URL.empty)
      assertTrue(actual == expected)
    },
  )

}
