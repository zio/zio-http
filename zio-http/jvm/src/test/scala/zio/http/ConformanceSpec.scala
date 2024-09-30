package zio.http

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.http._

object ConformanceSpec extends ZIOSpecDefault {

  /**
   * This test suite is inspired by and built upon the findings from the
   * research paper: "Who's Breaking the Rules? Studying Conformance to the HTTP
   * Specifications and its Security Impact" by Jannis Rautenstrauch and Ben
   * Stock, presented at the 19th ACM Asia Conference on Computer and
   * Communications Security (ASIA CCS) 2024.
   *
   * Paper URL: https://doi.org/10.1145/3634737.3637678 
   * GitHub Project: https://github.com/cispa/http-conformance
   */

  val validUrl = URL.decode("http://example.com").toOption.getOrElse(URL.root)

  override def spec =
    suite("ConformanceSpec")(
      suite("Statuscodes")(
        test("should not send body for 204 No Content responses(code_204_no_additional_content)") {
          val app = Routes(
            Method.GET / "no-content" -> Handler.fromResponse(
              Response.status(Status.NoContent),
            ),
          )

          val request = Request.get("/no-content")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.NoContent,
            response.body.isEmpty,
          )
        },
        test("should not send body for 205 Reset Content responses(code_205_no_content_allowed)") {
          val app = Routes(
            Method.GET / "reset-content" -> Handler.fromResponse(
              Response.status(Status.ResetContent),
            ),
          )

          val request = Request.get("/reset-content")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(response.status == Status.ResetContent, response.body.isEmpty)
        },
        test("should include Content-Range for 206 Partial Content response(code_206_content_range)") {
          val app = Routes(
            Method.GET / "partial" -> Handler.fromResponse(
              Response
                .status(Status.PartialContent)
                .addHeader(Header.ContentRange.StartEnd("bytes", 0, 14)),
            ),
          )

          val request = Request.get("/partial")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.PartialContent,
            response.headers.contains(Header.ContentRange.name),
          )
        },
        test(
          "should not include Content-Range in header for multipart/byteranges response(code_206_content_range_of_multiple_part_response)",
        ) {
          val boundary = zio.http.Boundary("A12345")

          val app = Routes(
            Method.GET / "partial" -> Handler.fromResponse(
              Response
                .status(Status.PartialContent)
                .addHeader(Header.ContentType(MediaType("multipart", "byteranges"), Some(boundary))),
            ),
          )

          val request = Request.get("/partial")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.PartialContent,
            !response.headers.contains(Header.ContentRange.name),
            response.headers.contains(Header.ContentType.name),
          )
        },
        test("should include necessary headers in 206 Partial Content response(code_206_headers)") {
          val app = Routes(
            Method.GET / "partial" -> Handler.fromResponse(
              Response
                .status(Status.PartialContent)
                .addHeader(Header.ETag.Strong("abc"))
                .addHeader(Header.CacheControl.MaxAge(3600)),
            ),
            Method.GET / "full"    -> Handler.fromResponse(
              Response
                .status(Status.Ok)
                .addHeader(Header.ETag.Strong("abc"))
                .addHeader(Header.CacheControl.MaxAge(3600)),
            ),
          )

          val requestWithRange    =
            Request.get("/partial").addHeader(Header.Range.Single("bytes", 0, Some(14)))
          val requestWithoutRange = Request.get("/full")

          for {
            responseWithRange    <- app.runZIO(requestWithRange)
            responseWithoutRange <- app.runZIO(requestWithoutRange)
          } yield assertTrue(
            responseWithRange.status == Status.PartialContent,
            responseWithRange.headers.contains(Header.ETag.name),
            responseWithRange.headers.contains(Header.CacheControl.name),
            responseWithoutRange.status == Status.Ok,
          )
        },
        test("should include WWW-Authenticate header for 401 Unauthorized response(code_401_www_authenticate)") {
          val app = Routes(
            Method.GET / "unauthorized" -> Handler.fromResponse(
              Response
                .status(Status.Unauthorized)
                .addHeader(Header.WWWAuthenticate.Basic(Some("simple"))),
            ),
          )

          val request = Request.get("/unauthorized")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.Unauthorized,
            response.headers.contains(Header.WWWAuthenticate.name),
          )
        },
        test("should include Allow header for 405 Method Not Allowed response(code_405_allow)") {
          val app = Routes(
            Method.POST / "not-allowed" -> Handler.fromResponse(
              Response
                .status(Status.Ok),
            ),
          )

          val request = Request.get("/not-allowed")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.MethodNotAllowed,
            response.headers.contains(Header.Allow.name),
          )
        },
        test(
          "should include Proxy-Authenticate header for 407 Proxy Authentication Required response(code_407_proxy_authenticate)",
        ) {
          val app = Routes(
            Method.GET / "proxy-auth" -> Handler.fromResponse(
              Response
                .status(Status.ProxyAuthenticationRequired)
                .addHeader(
                  Header.ProxyAuthenticate(Header.AuthenticationScheme.Basic, Some("proxy")),
                ),
            ),
          )

          val request = Request.get("/proxy-auth")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.ProxyAuthenticationRequired,
            response.headers.contains(Header.ProxyAuthenticate.name),
          )
        },
        test("should return 304 without content(code_304_no_content)") {
          val app = Routes(
            Method.GET / "no-content" -> Handler.fromResponse(
              Response
                .status(Status.NotModified)
                .copy(body = Body.empty),
            ),
          )

          val request = Request.get("/no-content")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.NotModified,
            response.body.isEmpty,
          )
        },
        test("should return 304 with correct headers(code_304_headers)") {
          val headers = Headers(
            Header.ETag.Strong("abc"),
            Header.CacheControl.MaxAge(3600),
            Header.Vary("Accept-Encoding"),
          )

          val app = Routes(
            Method.GET / "with-headers" -> Handler.fromResponse(
              Response
                .status(Status.NotModified)
                .addHeaders(headers),
            ),
          )

          val request = Request.get("/with-headers")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.NotModified,
            response.headers.contains(Header.ETag.name),
            response.headers.contains(Header.CacheControl.name),
            response.headers.contains(Header.Vary.name),
          )
        },
        test("should include Location header in 300 MULTIPLE CHOICES response(code_300_location)") {
          val testUrl = URL.decode("/People.html#tim").toOption.getOrElse(URL.root)

          val validResponse = Response
            .status(Status.MultipleChoices)
            .addHeader(Header.Location(testUrl))

          val invalidResponse = Response
            .status(Status.MultipleChoices)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.MultipleChoices,
            responseValid.headers.contains(Header.Location.name),
            responseInvalid.status == Status.MultipleChoices,
            !responseInvalid.headers.contains(Header.Location.name),
          )
        },
        test("300 MULTIPLE CHOICES response should have body content(code_300_metadata)") {
          val validResponse = Response
            .status(Status.MultipleChoices)
            .copy(body = Body.fromString("<div>ABC</div>"))

          val invalidResponse = Response
            .status(Status.MultipleChoices)
            .copy(body = Body.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            validBody       <- responseValid.body.asString
            responseInvalid <- app.runZIO(Request.get("/invalid"))
            invalidBody     <- responseInvalid.body.asString

          } yield assertTrue(
            responseValid.status == Status.MultipleChoices,
            validBody.contains("ABC"),
            responseInvalid.status == Status.MultipleChoices,
            invalidBody.isEmpty,
          )
        },
        test("should not require body content for HEAD requests(code_300_metadata)") {
          val response = Response
            .status(Status.MultipleChoices)
            .copy(body = Body.empty)
          val app      = Routes(
            Method.HEAD / "head" -> Handler.fromResponse(response),
          )

          for {
            headResponse <- app.runZIO(Request.head("/head"))
          } yield assertTrue(
            headResponse.status == Status.MultipleChoices,
            headResponse.body.isEmpty,
          )
        },
        test("should include Location header in 301 MOVED PERMANENTLY response(code_301_location)") {

          val validResponse = Response
            .status(Status.MovedPermanently)
            .addHeader(Header.Location(validUrl))

          val invalidResponse = Response
            .status(Status.MovedPermanently)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.MovedPermanently,
            responseValid.headers.contains(Header.Location.name),
            responseInvalid.status == Status.MovedPermanently,
            !responseInvalid.headers.contains(Header.Location.name),
          )
        },
        test("should include Location header in 302 FOUND response(code_302_location)") {

          val validResponse = Response
            .status(Status.Found)
            .addHeader(Header.Location(validUrl))

          val invalidResponse = Response
            .status(Status.Found)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.Found,
            responseValid.headers.contains(Header.Location.name),
            responseInvalid.status == Status.Found,
            !responseInvalid.headers.contains(Header.Location.name),
          )
        },
        test("should include Location header in 303 SEE OTHER response(code_303_location)") {

          val validResponse = Response
            .status(Status.SeeOther)
            .addHeader(Header.Location(validUrl))

          val invalidResponse = Response
            .status(Status.SeeOther)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.SeeOther,
            responseValid.headers.contains(Header.Location.name),
            responseInvalid.status == Status.SeeOther,
            !responseInvalid.headers.contains(Header.Location.name),
          )
        },
        test("should include Location header in 307 TEMPORARY REDIRECT response(code_307_location)") {

          val validResponse = Response
            .status(Status.TemporaryRedirect)
            .addHeader(Header.Location(validUrl))

          val invalidResponse = Response
            .status(Status.TemporaryRedirect)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.TemporaryRedirect,
            responseValid.headers.contains(Header.Location.name),
            responseInvalid.status == Status.TemporaryRedirect,
            !responseInvalid.headers.contains(Header.Location.name),
          )
        },
        test("should include Location header in 308 PERMANENT REDIRECT response(code_308_location)") {

          val validResponse = Response
            .status(Status.PermanentRedirect)
            .addHeader(Header.Location(validUrl))

          val invalidResponse = Response
            .status(Status.PermanentRedirect)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.PermanentRedirect,
            responseValid.headers.contains(Header.Location.name),
            responseInvalid.status == Status.PermanentRedirect,
            !responseInvalid.headers.contains(Header.Location.name),
          )
        },
        test(
          "should include Retry-After header in 413 Content Too Large response if condition is temporary (code_413_retry_after)",
        ) {
          val validResponse = Response
            .status(Status.RequestEntityTooLarge)
            .addHeader(Header.RetryAfter.ByDuration(10.seconds))

          val invalidResponse = Response
            .status(Status.RequestEntityTooLarge)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.RequestEntityTooLarge,
            responseValid.headers.contains(Header.RetryAfter.name),
            responseInvalid.status == Status.RequestEntityTooLarge,
            !responseInvalid.headers.contains(Header.RetryAfter.name),
          )
        },
        test(
          "should include Accept or Accept-Encoding header in 415 Unsupported Media Type response (code_415_unsupported_media_type)",
        ) {
          val validResponse = Response
            .status(Status.UnsupportedMediaType)
            .addHeader(Header.Accept(MediaType.application.json))

          val invalidResponse = Response
            .status(Status.UnsupportedMediaType)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.UnsupportedMediaType,
            responseValid.headers.contains(Header.Accept.name) ||
              responseValid.headers.contains(Header.AcceptEncoding.name),
            responseInvalid.status == Status.UnsupportedMediaType,
            !responseInvalid.headers.contains(Header.Accept.name) &&
              !responseInvalid.headers.contains(Header.AcceptEncoding.name),
          )
        },
        test("should include Content-Range header in 416 Range Not Satisfiable response (code_416_content_range)") {
          val validResponse = Response
            .status(Status.RequestedRangeNotSatisfiable)
            .addHeader(Header.ContentRange.RangeTotal("bytes", 47022))

          val invalidResponse = Response
            .status(Status.RequestedRangeNotSatisfiable)
            .addHeader(Header.Custom("Content-Range", ",;"))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.status == Status.RequestedRangeNotSatisfiable,
            responseValid.headers.contains(Header.ContentRange.name),
            responseInvalid.status == Status.RequestedRangeNotSatisfiable,
            responseInvalid.headers.contains(Header.ContentRange.name),
            responseInvalid.headers.get(Header.ContentRange.name).contains(",;"),
          )
        },
      ),
      suite("HTTP Headers")(
        test("should not include Content-Length header for 2XX CONNECT responses(content_length_2XX_connect)") {
          val app = Routes(
            Method.CONNECT / "" -> Handler.fromResponse(
              Response.status(Status.Ok),
            ),
          )

          val decodedUrl = URL.decode("https://example.com:443")

          val request = decodedUrl match {
            case Right(url) => Request(method = Method.CONNECT, url = url)
            case Left(_)    => throw new RuntimeException("Failed to decode the URL")
          }

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.Ok,
            !response.headers.contains(Header.ContentLength.name),
          )
        },
        test("should not include Transfer-Encoding header for 2XX CONNECT responses(transfer_encoding_2XX_connect)") {
          val app = Routes(
            Method.CONNECT / "" -> Handler.fromResponse(
              Response.status(Status.Ok),
            ),
          )

          val decodedUrl = URL.decode("https://example.com:443")

          val request = decodedUrl match {
            case Right(url) => Request(method = Method.CONNECT, url = url)
            case Left(_)    => throw new RuntimeException("Failed to decode the URL")
          }

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.Ok,
            !response.headers.contains(Header.TransferEncoding.name),
          )
        },
        test("should not return overly detailed Server header(server_header_long)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Server", "SimpleServer"))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Server", "a" * 101))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield {
            assertTrue(
              responseValid.headers.get(Header.Server.name).exists(_.length <= 100),
              responseInvalid.headers.get(Header.Server.name).exists(_.length > 100),
            )
          }
        },
        test("should include Content-Type header for responses with content(content_type_header_required)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.ContentType(MediaType.text.html))
            .copy(body = Body.fromString("<div>ABC</div>"))

          val invalidResponse = Response
            .status(Status.Ok)
            .copy(body = Body.fromString("<div>ABC</div>"))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield {
            assertTrue(
              responseValid.headers.contains(Header.ContentType.name),
              !responseInvalid.headers.contains(Header.ContentType.name),
            )
          }
        },
        test("should include Accept-Patch header when PATCH is supported(accept_patch_presence)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.AcceptPatch(NonEmptyChunk(MediaType.application.json)))

          val invalidResponse = Response
            .status(Status.Ok)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.OPTIONS / "valid"   -> Handler.fromResponse(validResponse),
            Method.OPTIONS / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.options("/valid"))
            responseInvalid <- app.runZIO(Request.options("/invalid"))
          } yield {
            assertTrue(
              responseValid.headers.contains(Header.AcceptPatch.name),
              !responseInvalid.headers.contains(Header.AcceptPatch.name),
            )
          }
        },
        test("should include Date header in responses (date_header_required)") {
          val validDate = ZonedDateTime.parse("Thu, 20 Mar 2025 20:03:00 GMT", DateTimeFormatter.RFC_1123_DATE_TIME)

          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Date(validDate))

          val invalidResponse = Response
            .status(Status.Ok)
            .copy(headers = Headers.empty)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.headers.contains(Header.Date.name),
            !responseInvalid.headers.contains(Header.Date.name),
          )
        },
        suite("CSP Header")(
          test("should not send more than one CSP header (duplicate_csp)") {
            val validResponse = Response
              .status(Status.Ok)
              .addHeader(Header.ContentSecurityPolicy.defaultSrc(Header.ContentSecurityPolicy.Source.Self))

            val invalidResponse = Response
              .status(Status.Ok)
              .addHeader(Header.ContentSecurityPolicy.defaultSrc(Header.ContentSecurityPolicy.Source.Self))
              .addHeader(Header.ContentSecurityPolicy.imgSrc(Header.ContentSecurityPolicy.Source.Self))

            val app = Routes(
              Method.GET / "valid"   -> Handler.fromResponse(validResponse),
              Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
            )

            for {
              responseValid   <- app.runZIO(Request.get("/valid"))
              responseInvalid <- app.runZIO(Request.get("/invalid"))
            } yield {
              val cspHeadersValid   = responseValid.headers.toList.collect {
                case h if h.headerName == Header.ContentSecurityPolicy.name => h
              }
              val cspHeadersInvalid = responseInvalid.headers.toList.collect {
                case h if h.headerName == Header.ContentSecurityPolicy.name => h
              }

              assertTrue(
                cspHeadersValid.length == 1,
                cspHeadersInvalid.length > 1,
              )
            }
          },
          // Note: Content-Security-Policy-Report-Only Header to be Supported
        ),
      ),
      suite("sts")(
        // Note: Strict-Transport-Security Header to be Supported

      ),
      suite("Transfer-Encoding")(
        suite("no_transfer_encoding_1xx_204")(
          test("should return valid when Transfer-Encoding is not present for 1xx or 204 status") {
            val app = Routes(
              Method.GET / "no-content" -> Handler.fromResponse(
                Response.status(Status.NoContent),
              ),
              Method.GET / "continue"   -> Handler.fromResponse(
                Response.status(Status.Continue),
              ),
            )
            for {
              responseNoContent <- app.runZIO(Request.get("/no-content"))
              responseContinue  <- app.runZIO(Request.get("/continue"))
            } yield assertTrue(responseNoContent.status == Status.NoContent) &&
              assertTrue(!responseNoContent.headers.contains(Header.TransferEncoding.name)) &&
              assertTrue(responseContinue.status == Status.Continue) &&
              assertTrue(!responseContinue.headers.contains(Header.TransferEncoding.name))
          },
          test("should return invalid when Transfer-Encoding is present for 1xx or 204 status") {
            val app = Routes(
              Method.GET / "no-content" -> Handler.fromResponse(
                Response.status(Status.NoContent).addHeader(Header.TransferEncoding.Chunked),
              ),
              Method.GET / "continue"   -> Handler.fromResponse(
                Response.status(Status.Continue).addHeader(Header.TransferEncoding.Chunked),
              ),
            )

            for {
              responseNoContent <- app.runZIO(Request.get("/no-content"))
              responseContinue  <- app.runZIO(Request.get("/continue"))
            } yield assertTrue(responseNoContent.status == Status.NoContent) &&
              assertTrue(responseNoContent.headers.contains(Header.TransferEncoding.name)) &&
              assertTrue(responseContinue.status == Status.Continue) &&
              assertTrue(responseContinue.headers.contains(Header.TransferEncoding.name))
          },
        ),
        suite("transfer_encoding_http11")(
          test("should send Transfer-Encoding in response if request HTTP version is 1.1 or higher") {
            val app = Routes(
              Method.GET / "test" -> Handler.fromResponse(
                Response.ok.addHeader(Header.TransferEncoding.Chunked),
              ),
            )

            val request = Request.get("/test").copy(version = Version.`HTTP/1.1`)

            for {
              response <- app.runZIO(request)
            } yield assertTrue(
              response.status == Status.Ok,
              response.headers.contains(Header.TransferEncoding.name),
            )
          },
        ),
      ),
      suite("HTTP-Methods")(
        test("should not send body for HEAD requests(content_head_request)") {
          val route       = Routes(
            Method.GET / "test"  -> Handler.fromResponse(Response.text("This is the body")),
            Method.HEAD / "test" -> Handler.fromResponse(Response(status = Status.Ok)),
          )
          val app         = route
          val headRequest = Request.head("/test")
          for {
            response <- app.runZIO(headRequest)
          } yield assertTrue(
            response.status == Status.Ok,
            response.body.isEmpty,
          )
        },
        test("should not return 206, 304, or 416 status codes for POST requests(post_invalid_response_codes)") {

          val app = Routes(
            Method.POST / "test" -> Handler.fromResponse(Response.status(Status.Ok)),
          )

          for {
            res <- app.runZIO(Request.post("/test", Body.empty))

          } yield assertTrue(
            res.status != Status.PartialContent,
            res.status != Status.NotModified,
            res.status != Status.RequestedRangeNotSatisfiable,
            res.status == Status.Ok,
          )
        },
        test("should send the same headers for HEAD and GET requests (head_get_headers)") {
          val getResponse = Response
            .status(Status.Ok)
            .addHeader(Header.ContentType(MediaType.text.html))
            .addHeader(Header.Custom("X-Custom-Header", "value"))
            .copy(body = Body.fromString("<div>ABC</div>"))

          val app = Routes(
            Method.GET / "test"  -> Handler.fromResponse(getResponse),
            Method.HEAD / "test" -> Handler.fromResponse(getResponse.copy(body = Body.empty)),
          )

          for {
            getResponse  <- app.runZIO(Request.get("/test"))
            headResponse <- app.runZIO(Request.head("/test"))
            getHeaders  = getResponse.headers.toList.map(_.headerName).toSet
            headHeaders = headResponse.headers.toList.map(_.headerName).toSet
          } yield assertTrue(
            getHeaders == headHeaders,
          )
        },
        test("404 response for truly non-existent path") {
          val app     = Routes(
            Method.GET / "existing-path" -> Handler.ok,
          )
          val request = Request.get(URL(Path.root / "non-existent-path"))

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.NotFound,
          )
        },
        test("should reply with 501 for unknown HTTP methods (code_501_unknown_methods)") {
          val app = Routes(
            Method.GET / "test" -> Handler.fromResponse(Response.status(Status.Ok)),
          )

          val unknownMethodRequest = Request(method = Method.CUSTOM("ABC"), url = URL(Path.root / "test"))

          for {
            response <- app.runZIO(unknownMethodRequest)
          } yield assertTrue(
            response.status == Status.NotImplemented,
          )
        },
        test(
          "should reply with 405 when the request method is not allowed for the target resource (code_405_blocked_methods)",
        ) {
          val app = Routes(
            Method.GET / "test" -> Handler.fromResponse(Response.status(Status.Ok)),
          )

          // Testing a disallowed method (e.g., CONNECT)
          val connectMethodRequest = Request(method = Method.CONNECT, url = URL(Path.root / "test"))

          for {
            response <- app.runZIO(connectMethodRequest)
          } yield assertTrue(
            response.status == Status.MethodNotAllowed,
          )
        },
      ),
      suite("HTTP/1.1")(
        test("should not generate a bare CR in headers for HTTP/1.1(no_bare_cr)") {
          val app = Routes(
            Method.GET / "test" -> Handler.fromZIO {
              ZIO.succeed(
                Response
                  .status(Status.Ok)
                  .addHeader(Header.Custom("A", "1\r\nB: 2")),
              )
            },
          )

          val request = Request
            .get("/test")
            .copy(version = Version.Http_1_1)

          for {
            response <- app.runZIO(request)
            headersString = response.headers.toString
            isValid       = !headersString.contains("\r") || headersString.contains("\r\n")
          } yield assertTrue(isValid)
        },
        test("should allow one CRLF in front of the request line (allow_crlf_start)") {
          val crlfPrefix = "\r\n".getBytes

          val validRequest = Request
            .get("/valid")
            .withBody(Body.fromChunk(Chunk.fromArray(crlfPrefix ++ "GET /valid HTTP/1.1".getBytes)))

          val invalidRequest = Request
            .get("/invalid")
            .withBody(Body.fromChunk(Chunk.fromArray(crlfPrefix ++ "GET /invalid HTTP/1.1".getBytes)))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(Response.status(Status.Ok)),
            Method.GET / "invalid" -> Handler.fromResponse(Response.status(Status.NotFound)),
          )

          for {
            responseValid   <- app.runZIO(validRequest)
            responseInvalid <- app.runZIO(invalidRequest)
          } yield {
            assertTrue(
              responseValid.status.isSuccess || responseValid.status == Status.NotFound,
              responseInvalid.status == Status.NotFound,
            )
          }
        },
        test("should send a 'Connection: close' option in final response (close_option_in_final_response)") {
          val validRequest = Request
            .get("/valid")
            .addHeader(Header.Connection.Close)

          val invalidRequest = Request
            .get("/invalid")
            .addHeader(Header.Connection.KeepAlive)

          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Connection.Close)

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Connection.KeepAlive)

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(validRequest)
            responseInvalid <- app.runZIO(invalidRequest)
          } yield {
            assertTrue(
              responseValid.headers.toList.exists(h =>
                h.headerName == Header.Connection.name && h.renderedValue == "close",
              ),
              responseInvalid.headers.toList.exists(h =>
                h.headerName == Header.Connection.name && h.renderedValue == "keep-alive",
              ),
            )
          }
        },
      ),
      suite("HTTP")(
        test("should return 400 Bad Request if header contains CR, LF, or NULL(reject_fields_contaning_cr_lf_nul)") {
          val route = Method.GET / "test" -> Handler.ok
          val app   = Routes(route)

          val requestWithCRLFHeader = Request.get("/test").addHeader("InvalidHeader", "Value\r\n")
          val requestWithNullHeader = Request.get("/test").addHeader("InvalidHeader", "Value\u0000")

          for {
            responseCRLF <- app.runZIO(requestWithCRLFHeader)
            responseNull <- app.runZIO(requestWithNullHeader)
          } yield {
            assertTrue(responseCRLF.status == Status.BadRequest) &&
            assertTrue(responseNull.status == Status.BadRequest)
          }
        },
        test("should send Upgrade header with 426 Upgrade Required response(send_upgrade_426)") {
          val app = Routes(
            Method.GET / "test" -> Handler.fromResponse(
              Response
                .status(Status.UpgradeRequired)
                .addHeader(Header.Upgrade.Protocol("https", "1.1")),
            ),
          )

          val request = Request.get("/test")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.UpgradeRequired,
            response.headers.contains(Header.Upgrade.name),
          )
        },
        test("should send Upgrade header with 101 Switching Protocols response(send_upgrade_101)") {
          val app = Routes(
            Method.GET / "switch" -> Handler.fromResponse(
              Response
                .status(Status.SwitchingProtocols)
                .addHeader(Header.Upgrade.Protocol("https", "1.1")),
            ),
          )

          val request = Request.get("/switch")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.SwitchingProtocols,
            response.headers.contains(Header.Upgrade.name),
          )
        },
        test("should not include Content-Length header for 1xx and 204 No Content responses(content_length_1XX_204)") {
          val route1xxContinue = Method.GET / "continue" -> Handler.fromResponse(Response(status = Status.Continue))
          val route1xxSwitch   =
            Method.GET / "switching-protocols" -> Handler.fromResponse(Response(status = Status.SwitchingProtocols))
          val route1xxProcess =
            Method.GET / "processing" -> Handler.fromResponse(Response(status = Status.Processing))
          val route204NoContent =
            Method.GET / "no-content" -> Handler.fromResponse(Response(status = Status.NoContent))

          val app = Routes(route1xxContinue, route1xxSwitch, route1xxProcess, route204NoContent)

          val requestContinue  = Request.get("/continue")
          val requestSwitch    = Request.get("/switching-protocols")
          val requestProcess   = Request.get("/processing")
          val requestNoContent = Request.get("/no-content")

          for {
            responseContinue  <- app.runZIO(requestContinue)
            responseSwitch    <- app.runZIO(requestSwitch)
            responseProcess   <- app.runZIO(requestProcess)
            responseNoContent <- app.runZIO(requestNoContent)

          } yield assertTrue(
            !responseContinue.headers.contains(Header.ContentLength.name),
            !responseSwitch.headers.contains(Header.ContentLength.name),
            !responseProcess.headers.contains(Header.ContentLength.name),
            !responseNoContent.headers.contains(Header.ContentLength.name),
          )
        },
        test(
          "should not switch to a protocol not indicated by the client in the Upgrade header(switch_protocol_without_client)",
        ) {
          val app = Routes(
            Method.GET / "switch" -> Handler.fromFunctionZIO { (request: Request) =>
              val clientUpgrade = request.headers.get(Header.Upgrade.name)

              ZIO.succeed {
                clientUpgrade match {
                  case Some("https/1.1") =>
                    Response
                      .status(Status.SwitchingProtocols)
                      .addHeader(Header.Upgrade.Protocol("https", "1.1"))
                  case Some(_)           =>
                    Response.status(Status.BadRequest)
                  case None              =>
                    Response.status(Status.Ok)
                }
              }
            },
          )

          val requestWithUpgrade = Request
            .get("/switch")
            .addHeader(Header.Upgrade.Protocol("https", "1.1"))

          val requestWithUnsupportedUpgrade = Request
            .get("/switch")
            .addHeader(Header.Upgrade.Protocol("unsupported", "1.0"))

          val requestWithoutUpgrade = Request.get("/switch")

          for {
            responseWithUpgrade            <- app.runZIO(requestWithUpgrade)
            responseWithUnsupportedUpgrade <- app.runZIO(requestWithUnsupportedUpgrade)
            responseWithoutUpgrade         <- app.runZIO(requestWithoutUpgrade)

          } yield assertTrue(
            responseWithUpgrade.status == Status.SwitchingProtocols,
            responseWithUpgrade.headers.contains(Header.Upgrade.name),
            responseWithUnsupportedUpgrade.status == Status.BadRequest,
            responseWithoutUpgrade.status == Status.Ok,
          )
        },
        test(
          "should send 100 Continue before 101 Switching Protocols when both Upgrade and Expect headers are present(continue_before_upgrade)",
        ) {
          val continueHandler = Handler.fromZIO {
            ZIO.succeed(Response.status(Status.Continue))
          }

          val switchingProtocolsHandler = Handler.fromZIO {
            ZIO.succeed(
              Response
                .status(Status.SwitchingProtocols)
                .addHeader(Header.Connection.KeepAlive)
                .addHeader(Header.Upgrade.Protocol("https", "1.1")),
            )
          }
          val app                       = Routes(
            Method.POST / "upgrade" -> continueHandler,
            Method.GET / "switch"   -> switchingProtocolsHandler,
          )
          val initialRequest            = Request
            .post("/upgrade", Body.empty)
            .addHeader(Header.Expect.`100-continue`)
            .addHeader(Header.Connection.KeepAlive)
            .addHeader(Header.Upgrade.Protocol("https", "1.1"))

          val followUpRequest = Request.get("/switch")

          for {
            firstResponse  <- app.runZIO(initialRequest)
            secondResponse <- app.runZIO(followUpRequest)

          } yield assertTrue(
            firstResponse.status == Status.Continue,
            secondResponse.status == Status.SwitchingProtocols,
            secondResponse.headers.contains(Header.Upgrade.name),
            secondResponse.headers.contains(Header.Connection.name),
          )
        },
        test("should not return forbidden duplicate headers in response(duplicate_fields)") {
          val app = Routes(
            Method.GET / "test" -> Handler.fromResponse(
              Response
                .status(Status.Ok)
                .addHeader(Header.XFrameOptions.Deny)
                .addHeader(Header.XFrameOptions.SameOrigin),
            ),
          )
          for {
            response <- app.runZIO(Request.get("/test"))
          } yield {
            val xFrameOptionsHeaders = response.headers.toList.collect {
              case h if h.headerName == Header.XFrameOptions.name => h
            }
            assertTrue(xFrameOptionsHeaders.length == 1)
          }
        },
        suite("Content-Length")(
          test("Content-Length in HEAD must match the one in GET (content_length_same_head_get)") {
            val getResponse = Response
              .status(Status.Ok)
              .addHeader(Header.ContentLength(14))
              .copy(body = Body.fromString("<div>ABC</div>"))

            val app = Routes(
              Method.GET / "test"  -> Handler.fromResponse(getResponse),
              Method.HEAD / "test" -> Handler.fromResponse(getResponse.copy(body = Body.empty)),
            )

            for {
              getResponse  <- app.runZIO(Request.get("/test"))
              headResponse <- app.runZIO(Request.head("/test"))
              getContentLength  = getResponse.headers.get(Header.ContentLength.name).map(_.toInt)
              headContentLength = headResponse.headers.get(Header.ContentLength.name).map(_.toInt)
            } yield assertTrue(
              headContentLength == getContentLength,
            )
          },
          test("Content-Length in 304 Not Modified must match the one in 200 OK (content_length_same_304_200)") {
            val app = Routes(
              Method.GET / "test" -> Handler.fromFunction { (request: Request) =>
                request.headers.get(Header.IfModifiedSince.name) match {
                  case Some(_) =>
                    Response.status(Status.NotModified).addHeader(Header.ContentLength(14)).copy(body = Body.empty)
                  case None    =>
                    Response
                      .status(Status.Ok)
                      .addHeader(Header.ContentLength(14))
                      .copy(body = Body.fromString("<div>ABC</div>"))
                }
              },
            )

            val conditionalRequest = Request
              .get("/test")
              .addHeader(
                Header.IfModifiedSince(
                  ZonedDateTime.parse("Thu, 20 Mar 2025 07:28:00 GMT", DateTimeFormatter.RFC_1123_DATE_TIME),
                ),
              )

            for {
              normalResponse      <- app.runZIO(Request.get("/test"))
              conditionalResponse <- app.runZIO(conditionalRequest)
              normalContentLength      = normalResponse.headers.get(Header.ContentLength.name).map(_.toInt)
              conditionalContentLength = conditionalResponse.headers.get(Header.ContentLength.name).map(_.toInt)
            } yield assertTrue(
              normalContentLength == conditionalContentLength,
            )
          },
        ),
      ),
      suite("cache-control")(
        test("Cache-Control should not have quoted string for max-age directive(response_directive_max_age)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.CacheControl.MaxAge(5))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Cache-Control", """max-age="5""""))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.headers.get(Header.CacheControl.name).contains("max-age=5"),
            responseInvalid.headers.get(Header.CacheControl.name).contains("""max-age="5""""),
          )
        },
        test("Cache-Control should not have quoted string for s-maxage directive(response_directive_s_maxage)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.CacheControl.SMaxAge(10))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Cache-Control", """s-maxage="10""""))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.headers.get(Header.CacheControl.name).contains("s-maxage=10"),
            responseInvalid.headers.get(Header.CacheControl.name).contains("""s-maxage="10""""),
          )
        },
        test("Cache-Control should use quoted-string form for no-cache directive(response_directive_no_cache)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Cache-Control", """no-cache="age""""))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Cache-Control", "no-cache=age"))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.headers.get(Header.CacheControl.name).contains("""no-cache="age""""),
            responseInvalid.headers.get(Header.CacheControl.name).contains("no-cache=age"),
          )
        },
        test("Cache-Control should use quoted-string form for private directive(response_directive_private)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Cache-Control", """private="x-frame-options""""))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Cache-Control", "private=x-frame-options"))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield assertTrue(
            responseValid.headers.get(Header.CacheControl.name).contains("""private="x-frame-options""""),
            responseInvalid.headers.get(Header.CacheControl.name).contains("private=x-frame-options"),
          )
        },
      ),
      suite("cookies")(
        test("should not have duplicate cookie attributes in Set-Cookie header(duplicate_cookie_attribute)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.SetCookie(Cookie.Response("test", "test", path = Some(Path.root))))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Set-Cookie", "test=test; path=/; path=/abc"))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield {
            val validCookieAttributes   = responseValid.headers.toList.collect {
              case h if h.headerName == Header.SetCookie.name => h.renderedValue
            }
            val invalidCookieAttributes = responseInvalid.headers.toList.collect {
              case h if h.headerName == "Set-Cookie" => h.renderedValue
            }
            assertTrue(
              validCookieAttributes.nonEmpty,
              validCookieAttributes.exists(_.toLowerCase.contains("path=/")),
              !validCookieAttributes.exists(_.toLowerCase.contains("path=/abc")),
            ) &&
            assertTrue(
              invalidCookieAttributes.exists(_.contains("path=/")),
              invalidCookieAttributes.exists(_.contains("path=/abc")),
            )
          }
        },
        test("should not have duplicate cookies with the same name(duplicate_cookies)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.SetCookie(Cookie.Response("test", "test")))
            .addHeader(Header.SetCookie(Cookie.Response("test2", "test2")))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.SetCookie(Cookie.Response("test", "test")))
            .addHeader(Header.SetCookie(Cookie.Response("test", "test2")))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield {
            val validCookies   = responseValid.headers.toList.collect {
              case h if h.headerName == Header.SetCookie.name => h.renderedValue
            }
            val invalidCookies = responseInvalid.headers.toList.collect {
              case h if h.headerName == Header.SetCookie.name => h.renderedValue
            }
            assertTrue(
              validCookies.count(_.contains("test=")) == 1,
            ) &&
            assertTrue(
              invalidCookies.count(_.contains("test=")) == 2,
            )
          }
        },
        test("should use IMF-fixdate for cookie expiration date(cookie_IMF_fixdate)") {
          val validResponse = Response
            .status(Status.Ok)
            .addHeader(Header.SetCookie(Cookie.Response("test", "test", maxAge = Some(Duration.fromSeconds(86400)))))

          val invalidResponse = Response
            .status(Status.Ok)
            .addHeader(Header.Custom("Set-Cookie", "test=test; expires=Thu, 20 Mar 25 15:14:45 GMT"))

          val app = Routes(
            Method.GET / "valid"   -> Handler.fromResponse(validResponse),
            Method.GET / "invalid" -> Handler.fromResponse(invalidResponse),
          )

          for {
            responseValid   <- app.runZIO(Request.get("/valid"))
            responseInvalid <- app.runZIO(Request.get("/invalid"))
          } yield {
            val expiresValid   = responseValid.headers.toList.exists(_.renderedValue.contains("Expires="))
            val expiresInvalid =
              responseInvalid.headers.toList.exists(_.renderedValue.contains("expires=Thu, 20 Mar 25"))

            assertTrue(
              expiresValid,
              expiresInvalid,
            )
          }
        },
      ),
      suite("conformance")(
        test("should not include Content-Length header for 204 No Content responses") {
          val route = Method.GET / "no-content" -> Handler.fromResponse(Response(status = Status.NoContent))
          val app   = Routes(route)

          val request = Request.get("/no-content")
          for {
            response <- app.runZIO(request)
          } yield assertTrue(!response.headers.contains(Header.ContentLength.name))
        },
        test("should not send content for 304 Not Modified responses") {
          val app = Routes(
            Method.GET / "not-modified" -> Handler.fromResponse(
              Response.status(Status.NotModified),
            ),
          )

          val request = Request.get("/not-modified")

          for {
            response <- app.runZIO(request)
          } yield assertTrue(
            response.status == Status.NotModified,
            response.body.isEmpty,
            !response.headers.contains(Header.ContentLength.name),
            !response.headers.contains(Header.TransferEncoding.name),
          )
        },
      ),
    )
}
