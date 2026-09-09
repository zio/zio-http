package zio.http

import zio.test._

object AccessLogRequestIdSpec extends ZIOSpecDefault {

  private val Esc: String = "" + 27.toChar

  private def headersWith(id: String): Headers =
    Headers.empty.add("x-request-id", id)

  override def spec: Spec[Any, Any] =
    suite("AccessLogRequestIdSpec")(
      test("newline characters are stripped") {
        assertTrue(AccessLog.sanitizeRequestId("abc\ndef\r\nGHI") == "abcdefGHI")
      },
      test("ANSI escape sequences are stripped end to end") {
        val id = AccessLog.requestId(headersWith("abc" + Esc + "[31mdef"))
        assertTrue(id == "abc31mdef")
      },
      test("5KB header value is truncated to 128 chars") {
        val big = "a" * 5120
        val id  = AccessLog.requestId(headersWith(big))
        assertTrue(id.length == 128, id == "a" * 128)
      },
      test("value that is empty after sanitizing yields a generated UUID") {
        val id = AccessLog.requestId(headersWith("!!!"))
        assertTrue(id.nonEmpty, id.length == 36, !id.contains("!"))
      },
      test("plain token passes through unchanged") {
        val id = AccessLog.requestId(headersWith("req-1_~.abcXYZ"))
        assertTrue(id == "req-1_~.abcXYZ")
      },
      test("absent header yields a generated UUID") {
        val id = AccessLog.requestId(Headers.empty)
        assertTrue(id.nonEmpty, id.length == 36)
      },
    )
}
