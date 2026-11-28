/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.h1

import zio.test._

object H1ModelConversionSpec extends ZIOSpecDefault {
  override def spec =
    suite("H1ModelConversionSpec")(
      suite("header field pairs")(
        test("converts headers to field pairs and back") {
          val headers = H1Headers(List(H1Header("Host", "x"), H1Header("X-A", "1")))

          assertTrue(H1ModelConversion.fromFieldPairs(H1ModelConversion.toFieldPairs(headers)) == Right(headers))
        },
        test("rejects invalid field pairs") {
          assertTrue(
            H1ModelConversion.fromFieldPairs(List(("bad name", "v"))) ==
              Left(H1Error.InvalidMessage("invalid header name: 'bad name'")),
          )
        },
        test("converts an empty field list to empty headers") {
          assertTrue(H1ModelConversion.fromFieldPairs(Nil) == Right(H1Headers.Empty))
        },
      ),
      suite("connection preference")(
        test("defaults to keep-alive without a Connection header") {
          assertTrue(
            H1ModelConversion.connectionPreference(H1Headers.Empty) == H1ConnectionPreference.KeepAlive,
          )
        },
        test("detects Connection: close case-insensitively") {
          assertTrue(
            H1ModelConversion.connectionPreference(H1Headers(List(H1Header("Connection", "CLOSE")))) ==
              H1ConnectionPreference.Close,
          )
        },
        test("detects close inside a token list") {
          assertTrue(
            H1ModelConversion.connectionPreference(H1Headers(List(H1Header("Connection", "keep-alive, close")))) ==
              H1ConnectionPreference.Close,
          )
        },
        test("keeps alive for unrelated connection tokens") {
          assertTrue(
            H1ModelConversion.connectionPreference(H1Headers(List(H1Header("Connection", "keep-alive")))) ==
              H1ConnectionPreference.KeepAlive,
          )
        },
      ),
      suite("header smart constructor")(
        test("accepts valid names and values") {
          assertTrue(H1Header.fromStrings("X-Custom-9", "v ~!") == Right(H1Header("X-Custom-9", "v ~!")))
        },
        test("rejects an empty name") {
          assertTrue(H1Header.fromStrings("", "v").isLeft)
        },
        test("rejects a value with a control character") {
          assertTrue(H1Header.fromStrings("X-A", "a\nb").isLeft)
        },
      ),
      suite("framing resolution")(
        test("resolves chunked case-insensitively") {
          val headers = H1Headers(List(H1Header("Transfer-Encoding", "Chunked")))

          assertTrue(
            H1Framing.resolve(headers, H1Limits.Default, isResponse = false, statusCode = 0, requestMethod = "GET") ==
              Right(H1BodyFraming.Chunked),
          )
        },
        test("resolves a zero Content-Length to a fixed empty body") {
          val headers = H1Headers(List(H1Header("Content-Length", "0")))

          assertTrue(
            H1Framing.resolve(headers, H1Limits.Default, isResponse = false, statusCode = 0, requestMethod = "POST") ==
              Right(H1BodyFraming.Fixed(0L)),
          )
        },
        test("resolves no framing headers on a request to empty") {
          assertTrue(
            H1Framing
              .resolve(H1Headers.Empty, H1Limits.Default, isResponse = false, statusCode = 0, requestMethod = "GET") ==
              Right(H1BodyFraming.Empty),
          )
        },
        test("ignores framing on a 304 response") {
          val headers = H1Headers(List(H1Header("Content-Length", "5")))

          assertTrue(
            H1Framing.resolve(headers, H1Limits.Default, isResponse = true, statusCode = 304, requestMethod = "GET") ==
              Right(H1BodyFraming.Empty),
          )
        },
        test("ignores framing on a HEAD response") {
          val headers = H1Headers(List(H1Header("Content-Length", "5")))

          assertTrue(
            H1Framing.resolve(headers, H1Limits.Default, isResponse = true, statusCode = 200, requestMethod = "HEAD") ==
              Right(H1BodyFraming.Empty),
          )
        },
      ),
    )
}
