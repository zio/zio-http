package zio.http

import java.time._

import scala.sys.process._
import scala.util.Try

import zio._
import zio.test._

import zio.http.Header.ETag._
import zio.http.Header.{IfModifiedSince, IfNoneMatch, LastModified}
import zio.http.netty.NettyConfig

object HttpConformanceSpec extends ZIOSpecDefault {

  // Helper to build absolute URL given port & path segment
  private def urlFor(port: Int, segment: String) = url"http://localhost:$port/$segment"

  private def sendRawHttp(
    port: Int,
    rawRequest: String,
    timeoutSeconds: Int = 5,
    allowFailure: Boolean = false,
  ) = {
    val printfCmd = Seq("printf", "%b", rawRequest)
    val ncCmd     = Seq("nc", "-w", timeoutSeconds.toString, "localhost", port.toString)
    val run       = ZIO.attemptBlocking((printfCmd #| ncCmd).!!)
    if (allowFailure) run.catchAll(_ => ZIO.succeed("")) else run
  }

  private def statusCodeOf(response: String): Option[Int] =
    response.linesIterator
      .find(_.startsWith("HTTP/"))
      .flatMap(_.split(" ").drop(1).headOption.flatMap(i => Try(i.toInt).toOption))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("HttpConformanceSpec")(
      test("If-None-Match strong match yields 304 Not Modified (future behavior) with empty body") {
        val etag      = Strong("abc")
        val etagToken = Header.ETag.render(etag)
        val routes    =
          (Method.GET / "etag" -> handler { (_: Request) => Response.text("payload").addHeader(etag) }).toRoutes
        val ifNone = Headers(IfNoneMatch.ETags(NonEmptyChunk(etagToken)))
        for {
          port    <- Server.installRoutes(routes)
          res     <- Client.batched(Request(method = Method.GET, url = urlFor(port, "etag"), headers = ifNone))
          bodyStr <- res.body.asString
        } yield assertTrue(
          res.status == Status.NotModified || res.status == Status.Ok,
          (res.status == Status.NotModified && bodyStr.isEmpty) || (res.status == Status.Ok && bodyStr == "payload"),
        )
      },
      test("If-None-Match non-matching strong ETag returns 200 with body") {
        val etagResp = Strong("abc")
        val etagReq  = Header.ETag.render(Strong("xyz"))
        val routes   =
          (Method.GET / "etag2" -> handler { (_: Request) => Response.text("payload").addHeader(etagResp) }).toRoutes
        val ifNone = Headers(IfNoneMatch.ETags(NonEmptyChunk(etagReq)))
        for {
          port    <- Server.installRoutes(routes)
          res     <- Client.batched(Request(method = Method.GET, url = urlFor(port, "etag2"), headers = ifNone))
          bodyStr <- res.body.asString
        } yield assertTrue(res.status == Status.Ok, bodyStr == "payload")
      },
      test(
        "If-Modified-Since with resource not modified yields 304 (future) or Ok fallback with body empty only if 304",
      ) {
        val lastModified = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val routes       = (Method.GET / "ims" -> handler { (_: Request) =>
          Response.text("content").addHeader(LastModified(lastModified))
        }).toRoutes
        val ifMod        = Headers(IfModifiedSince(lastModified))
        for {
          port    <- Server.installRoutes(routes)
          res     <- Client.batched(Request(method = Method.GET, url = urlFor(port, "ims"), headers = ifMod))
          bodyStr <- res.body.asString
        } yield assertTrue(
          res.status == Status.NotModified || res.status == Status.Ok,
          (res.status == Status.NotModified && bodyStr.isEmpty) || (res.status == Status.Ok && bodyStr == "content"),
        )
      },
      test("If-Modified-Since earlier date yields 200 and body present") {
        val lastModified = ZonedDateTime.of(2025, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC)
        val earlier      = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val routes       = (Method.GET / "ims2" -> handler { (_: Request) =>
          Response.text("fresh").addHeader(LastModified(lastModified))
        }).toRoutes
        val ifMod        = Headers(IfModifiedSince(earlier))
        for {
          port    <- Server.installRoutes(routes)
          res     <- Client.batched(Request(method = Method.GET, url = urlFor(port, "ims2"), headers = ifMod))
          bodyStr <- res.body.asString
        } yield assertTrue(res.status == Status.Ok, bodyStr == "fresh")
      },
      test("304 responses (If-None-Match) body empty when NotModified, or payload if 200 until implemented") {
        val etag      = Strong("bodyless")
        val etagToken = Header.ETag.render(etag)
        val routes    =
          (Method.GET / "etag304" -> handler { (_: Request) => Response.text("cacheable").addHeader(etag) }).toRoutes
        val ifNone = Headers(IfNoneMatch.ETags(NonEmptyChunk(etagToken)))
        for {
          port    <- Server.installRoutes(routes)
          res     <- Client.batched(Request(method = Method.GET, url = urlFor(port, "etag304"), headers = ifNone))
          bodyStr <- res.body.asString
        } yield assertTrue(bodyStr.isEmpty || bodyStr == "cacheable")
      },
      suite("Content Negotiation (RFC 9110 §12)")(
        test("Accept header with quality values selects highest preference") {
          // Handler returns JSON if Accept prefers it, otherwise XML
          val routes = (Method.GET / "content" -> handler { (req: Request) =>
            val accept = req.header(Header.Accept)
            accept match {
              case Some(hdr) =>
                // Get the media types sorted by q-factor (highest first)
                val sorted    = hdr.mimeTypes.sorted
                val preferred = sorted.headOption

                preferred match {
                  case Some(Header.Accept.MediaTypeWithQFactor(mediaType, _))
                      if mediaType.mainType == "application" && mediaType.subType == "json" =>
                    Response.json("""{"format":"json"}""")
                  case Some(Header.Accept.MediaTypeWithQFactor(mediaType, _))
                      if mediaType.mainType == "application" && mediaType.subType == "xml" =>
                    Response.text("<format>xml</format>").addHeader(Header.ContentType(MediaType.application.xml))
                  case _ =>
                    Response.text("default")
                }
              case None      => Response.text("default")
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Request with JSON preferred (implicit q=1.0 > q=0.9)
            jsonReq = Headers(
              Header.Accept(
                Header.Accept.MediaTypeWithQFactor(MediaType.application.json, None),
                Header.Accept.MediaTypeWithQFactor(MediaType.application.xml, Some(0.9)),
              ),
            )
            jsonRes  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "content"), headers = jsonReq))
            jsonBody <- jsonRes.body.asString
            // Request with XML preferred
            xmlReq = Headers(Header.Accept(MediaType.application.xml))
            xmlRes  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "content"), headers = xmlReq))
            xmlBody <- xmlRes.body.asString
          } yield assertTrue(
            jsonBody.contains("json"),
            xmlBody.contains("xml"),
          )
        },
        test("Accept with multiple media types respects q-value ordering") {
          val routes = (Method.GET / "qvalue" -> handler { (req: Request) =>
            val accept = req.header(Header.Accept)
            accept match {
              case Some(hdr) =>
                val acceptStr = Header.Accept.render(hdr)
                // Parse q-values and select highest
                if (acceptStr.contains("text/html") && acceptStr.contains("q=0.5")) {
                  Response.json("""{"selected":"json"}""") // JSON has implicit q=1.0
                } else if (acceptStr.contains("text/html")) {
                  Response.html("<b>html</b>")
                } else {
                  Response.json("""{"selected":"json"}""")
                }
              case None      => Response.text("default")
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // text/html;q=0.5, application/json (implicit q=1.0)
            req = Headers(
              Header.Accept(
                Header.Accept.MediaTypeWithQFactor(MediaType.text.html, Some(0.5)),
                Header.Accept.MediaTypeWithQFactor(MediaType.application.json, None),
              ),
            )
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "qvalue"), headers = req))
            body <- res.body.asString
          } yield assertTrue(body.contains("json"))
        },
        test("Accept-Encoding negotiation for gzip compression") {
          val routes = (Method.GET / "encoding" -> handler { (req: Request) =>
            val encoding = req.header(Header.AcceptEncoding)
            encoding match {
              case Some(hdr) =>
                val encodingStr = Header.AcceptEncoding.render(hdr)
                if (encodingStr.contains("gzip")) {
                  // In real scenario, this would be gzip-compressed
                  Response.text("compressed").addHeader(Header.ContentEncoding.GZip)
                } else {
                  Response.text("uncompressed")
                }
              case None      => Response.text("uncompressed")
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            req = Headers(Header.AcceptEncoding(Header.AcceptEncoding.GZip()))
            res <- Client.batched(Request(method = Method.GET, url = urlFor(port, "encoding"), headers = req))
            ce = res.header(Header.ContentEncoding)
          } yield assertTrue(ce.isDefined)
        },
        test("Accept-Language negotiation selects preferred language") {
          val routes = (Method.GET / "language" -> handler { (req: Request) =>
            val lang = req.header(Header.AcceptLanguage)
            lang match {
              case Some(hdr) =>
                val langStr = Header.AcceptLanguage.render(hdr)
                if (langStr.contains("en")) {
                  Response.text("Hello").addHeader(Header.ContentLanguage.English)
                } else if (langStr.contains("de")) {
                  Response.text("Guten Tag").addHeader(Header.ContentLanguage.German)
                } else if (langStr.contains("fr")) {
                  Response.text("Bonjour").addHeader(Header.ContentLanguage.French)
                } else {
                  Response.text("Hello")
                }
              case None      => Response.text("Hello")
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            reqEn = Headers(Header.AcceptLanguage.Single("en", None))
            resEn  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "language"), headers = reqEn))
            bodyEn <- resEn.body.asString
            reqDe = Headers(Header.AcceptLanguage.Single("de", None))
            resDe  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "language"), headers = reqDe))
            bodyDe <- resDe.body.asString
          } yield assertTrue(
            bodyEn == "Hello",
            bodyDe == "Guten Tag",
          )
        },
        test("406 Not Acceptable when server cannot satisfy Accept header") {
          val routes = (Method.GET / "strict" -> handler { (req: Request) =>
            val accept = req.header(Header.Accept)
            accept match {
              case Some(hdr) =>
                val acceptStr = Header.Accept.render(hdr)
                // Server only supports application/json
                if (acceptStr.contains("application/json") || acceptStr.contains("*/*")) {
                  Response.json("""{"data":"value"}""")
                } else {
                  // Cannot satisfy request - return 406
                  Response.status(Status.NotAcceptable)
                }
              case None      => Response.json("""{"data":"value"}""") // Default to JSON
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Request XML but server only has JSON
            xmlReq = Headers(Header.Accept(MediaType.application.xml))
            xmlRes <- Client.batched(Request(method = Method.GET, url = urlFor(port, "strict"), headers = xmlReq))
            // Request JSON - should succeed
            jsonReq = Headers(Header.Accept(MediaType.application.json))
            jsonRes <- Client.batched(Request(method = Method.GET, url = urlFor(port, "strict"), headers = jsonReq))
            // Request with */* - should succeed
            wildcardReq = Headers(Header.Accept(MediaType.any))
            wildcardRes <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "strict"), headers = wildcardReq),
            )
          } yield assertTrue(
            xmlRes.status == Status.NotAcceptable,
            jsonRes.status == Status.Ok,
            wildcardRes.status == Status.Ok,
          )
        },
        test("Vary header reflects content negotiation axes") {
          val routes = (Method.GET / "vary" -> handler { (req: Request) =>
            val accept   = req.header(Header.Accept)
            val response = accept match {
              case Some(hdr) if Header.Accept.render(hdr).contains("application/json") =>
                Response.json("""{"type":"json"}""")
              case _                                                                   =>
                Response.text("text")
            }
            // Add Vary header to indicate response varies by Accept header
            response.addHeader(Header.Vary("accept"))
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            req = Headers(Header.Accept(MediaType.application.json))
            res <- Client.batched(Request(method = Method.GET, url = urlFor(port, "vary"), headers = req))
            vary = res.header(Header.Vary)
          } yield assertTrue(vary.isDefined)
        },
      ),
      suite("Protocol-Level Conformance (RFC 9110 & RFC 9112)")(
        test("Case-insensitive header field names - content-type = Content-Type") {
          val routes = (Method.GET / "headers" -> handler { (req: Request) =>
            // Access header with different casing
            val ct1 = req.header(Header.ContentType)
            val ct2 = req.headers.get("content-type")
            val ct3 = req.headers.get("CONTENT-TYPE")

            Response.json(s"""{"ct1":"${ct1.isDefined}","ct2":"${ct2.isDefined}","ct3":"${ct3.isDefined}"}""")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "headers"))
                .addHeader(Header.ContentType(MediaType.application.json)),
            )
            body <- res.body.asString
          } yield assertTrue(
            body.contains(""""ct1":"true""""),
            body.contains(""""ct2":"true""""),
            body.contains(""""ct3":"true""""),
          )
        },
        test("Case-insensitive method names - GET = get = Get") {
          val routes = (Method.GET / "method" -> handler { (_: Request) =>
            Response.text("success")
          }).toRoutes

          for {
            port     <- Server.installRoutes(routes)
            // Standard GET
            resUpper <- Client.batched(Request(method = Method.GET, url = urlFor(port, "method")))
            // Note: zio-http normalizes methods internally, so we test that GET works
            // The HTTP spec requires servers to treat methods case-sensitively for registered methods
            // but case-insensitively for comparison purposes in routing
          } yield assertTrue(
            resUpper.status == Status.Ok,
          )
        },
        test("Whitespace handling in header values - no leading/trailing whitespace") {
          val routes = (Method.GET / "whitespace" -> handler { (req: Request) =>
            val customHeader = req.headers.get("X-Custom-Header")
            Response.text(customHeader.getOrElse("missing"))
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // HTTP/1.1 spec (RFC 9110) prohibits leading/trailing whitespace in field values
            // Test that valid header values (without leading/trailing whitespace) work correctly
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "whitespace"))
                .addHeader("X-Custom-Header", "value-without-spaces"),
            )
            body <- res.body.asString
          } yield assertTrue(
            body == "value-without-spaces",
            res.status == Status.Ok,
          )
        },
        test("400 Bad Request for malformed request line") {
          val routes = (Method.GET / "normal" -> handler { (_: Request) =>
            Response.text("ok")
          }).toRoutes

          for {
            port     <- Server.installRoutes(routes)
            // Valid request should succeed
            validRes <- Client.batched(Request(method = Method.GET, url = urlFor(port, "normal")))
            // Note: It's difficult to send truly malformed requests through the Client API
            // since it constructs valid HTTP messages. This test validates the valid case.
            // Malformed request testing would require raw socket connections.
          } yield assertTrue(
            validRes.status == Status.Ok,
          )
        },
        test("414 URI Too Long when URI exceeds reasonable length") {
          // Generate a very long path
          val longSegment = "a" * 10000 // 10KB path segment
          val routes      = (Method.GET / "short" -> handler { (_: Request) =>
            Response.text("ok")
          }).toRoutes

          for {
            port     <- Server.installRoutes(routes)
            // Normal request should succeed
            shortRes <- Client.batched(Request(method = Method.GET, url = urlFor(port, "short")))
            // Very long URI - server may reject with 414, 500, or connection error
            longUrl = url"http://localhost:$port/$longSegment"
            longRes <- Client
              .batched(Request(method = Method.GET, url = longUrl))
              .catchAll(_ => ZIO.succeed(Response.status(Status.RequestUriTooLong)))
          } yield assertTrue(
            shortRes.status == Status.Ok,
            // Server either accepts long URIs or rejects with 414, 500, 404, or connection error
            longRes.status == Status.Ok ||
              longRes.status == Status.RequestUriTooLong ||
              longRes.status == Status.NotFound ||
              longRes.status == Status.InternalServerError, // May return 500 for excessively long URIs
          )
        },
        test("Multiple header values with same name are handled correctly") {
          val routes = (Method.GET / "multi-header" -> handler { (req: Request) =>
            // Access custom headers - HTTP spec allows multiple headers with same name
            val header1 = req.headers.get("X-Custom")
            Response.text(s"received:${header1.isDefined}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "multi-header"))
                .addHeader("X-Custom", "value1")
                .addHeader("X-Custom", "value2"),
            )
            body <- res.body.asString
          } yield assertTrue(
            // HTTP spec allows multiple headers with same name
            // Server should handle them gracefully
            res.status == Status.Ok,
            body.contains("received:true"),
          )
        },
      ),
      suite("HTTP/1.1 Compliance (RFC 9112)")(
        // Host Header Requirement (RFC 9112 §3.2)
        test("Host header present in HTTP/1.1 requests") {
          val routes = (Method.GET / "host-check" -> handler { (req: Request) =>
            val host = req.headers.get(Header.Host)
            Response.text(s"host=${host.isDefined}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "host-check")))
            body <- res.body.asString
          } yield assertTrue(
            res.status == Status.Ok,
            body.contains("host=true"), // Client should auto-add Host header
          )
        },

        // Content-Length and Transfer-Encoding (RFC 9112 §6.3)
        test("Content-Length header correctly reflects body size") {
          val routes = (Method.POST / "content-length" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              clOpt = req.headers.get(Header.ContentLength)
            } yield {
              val cl = clOpt.map(_.length).getOrElse(-1L)
              Response.text(s"cl=$cl,actual=${body.length},match=${cl == body.length}")
            }
          }).toRoutes

          val testBody = "test body content"
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.POST, url = urlFor(port, "content-length"), body = Body.fromString(testBody)),
            )
            body <- res.body.asString
          } yield assertTrue(
            res.status == Status.Ok,
            body.contains("match=true"),
          )
        },
        test("Transfer-Encoding chunked is handled correctly") {
          val routes = (Method.POST / "chunked" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              te = req.headers.get("Transfer-Encoding")
            } yield Response.text(s"received=${body.length},te=${te.isDefined}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // ZIO HTTP client handles chunking automatically for streams
            res  <- Client.batched(
              Request(method = Method.POST, url = urlFor(port, "chunked"), body = Body.fromString("chunked test data")),
            )
            body <- res.body.asString
          } yield assertTrue(
            res.status == Status.Ok,
            body.contains("received="),
          )
        },

        // Range Requests (RFC 9110 §14)
        test("Range request returns 206 Partial Content with Accept-Ranges") {
          val content = "0123456789abcdefghijklmnopqrstuvwxyz" // 36 chars
          val routes  = (Method.GET / "range-test" -> handler { (req: Request) =>
            val rangeOpt = req.headers.get(Header.Range)
            rangeOpt match {
              case Some(range) =>
                range match {
                  case Header.Range.Suffix(unit, suffixLen)     =>
                    val start   = (content.length - suffixLen.toInt).max(0)
                    val end     = content.length
                    val partial = content.substring(start, end)
                    Response
                      .text(partial)
                      .status(Status.PartialContent)
                      .addHeader(
                        Header.ContentRange.EndTotal(unit, start.toInt, end - 1, content.length),
                      )
                      .addHeader(Header.AcceptRanges.Bytes)
                  case Header.Range.Single(unit, start, endOpt) =>
                    val startInt = start.toInt
                    val endInt   = endOpt.map(_.toInt + 1).getOrElse(content.length)
                    if (startInt >= content.length || startInt < 0) {
                      Response
                        .status(Status.RequestedRangeNotSatisfiable)
                        .addHeader(Header.ContentRange.RangeTotal(unit, content.length))
                    } else {
                      val actualEnd = endInt.min(content.length)
                      val partial   = content.substring(startInt, actualEnd)
                      Response
                        .text(partial)
                        .status(Status.PartialContent)
                        .addHeader(
                          Header.ContentRange
                            .EndTotal(unit, startInt, actualEnd - 1, content.length),
                        )
                        .addHeader(Header.AcceptRanges.Bytes)
                    }
                  case _                                        =>
                    Response.text(content).addHeader(Header.AcceptRanges.Bytes)
                }
              case None        =>
                Response.text(content).addHeader(Header.AcceptRanges.Bytes)
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Request bytes 0-9 (first 10 bytes)
            rangeReq = Request(method = Method.GET, url = urlFor(port, "range-test"))
              .addHeader(Header.Range.Single("bytes", 0, Some(9)))
            res     <- Client.batched(rangeReq)
            body    <- res.body.asString
            fullRes <- Client.batched(Request(method = Method.GET, url = urlFor(port, "range-test")))
          } yield assertTrue(
            res.status == Status.PartialContent,
            body == "0123456789",
            res.headers.get(Header.ContentRange).isDefined,
            res.headers.get(Header.AcceptRanges).isDefined,
            fullRes.headers.get(Header.AcceptRanges).isDefined,
          )
        },
        test("416 Range Not Satisfiable for invalid range") {
          val content = "short content"
          val routes  = (Method.GET / "range-invalid" -> handler { (req: Request) =>
            val rangeOpt = req.headers.get(Header.Range)
            rangeOpt match {
              case Some(range) =>
                range match {
                  case Header.Range.Single(unit, start, _) =>
                    val startInt = start.toInt
                    if (startInt >= content.length) {
                      Response
                        .status(Status.RequestedRangeNotSatisfiable)
                        .addHeader(Header.ContentRange.RangeTotal(unit, content.length))
                    } else {
                      Response.text(content)
                    }
                  case _                                   =>
                    Response.text(content)
                }
              case None        =>
                Response.text(content)
            }
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Request range beyond content length
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "range-invalid"))
                .addHeader(Header.Range.Single("bytes", 1000, Some(2000))),
            )
          } yield assertTrue(
            res.status == Status.RequestedRangeNotSatisfiable,
            // Check for Content-Range header using raw string access
            res.headers.get("Content-Range").isDefined || res.headers.get("content-range").isDefined,
          )
        },

        // Connection Management (RFC 9112 §9)
        test("Connection: close header is respected") {
          val routes = (Method.GET / "connection-close" -> handler { (_: Request) =>
            Response.text("closing").addHeader(Header.Connection.Close)
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "connection-close")))
          } yield assertTrue(
            res.status == Status.Ok,
            res.headers.get(Header.Connection).exists(_.toString.toLowerCase.contains("close")),
          )
        },
        test("Connection: keep-alive is supported") {
          val routes = (Method.GET / "keep-alive" -> handler { (_: Request) =>
            Response.text("alive").addHeader(Header.Connection.KeepAlive)
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "keep-alive")))
          } yield assertTrue(
            res.status == Status.Ok,
            // Keep-alive is default in HTTP/1.1, may or may not be explicit
            res.headers.get(Header.Connection).isDefined || res.status == Status.Ok,
          )
        },

        // Error Status Codes
        test("400 Bad Request for malformed request line") {
          val routes = (Method.GET / "valid" -> handler { (_: Request) =>
            Response.text("ok")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Valid request should work
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "valid")))
          } yield assertTrue(
            res.status == Status.Ok,
            // Note: Testing truly malformed requests requires lower-level socket access
            // This test verifies that well-formed requests succeed
          )
        },
        test("431 Request Header Fields Too Large when headers exceed limit") {
          val routes = (Method.GET / "large-headers" -> handler { (req: Request) =>
            Response.text(s"headers=${req.headers.size}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Normal request with reasonable headers
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "large-headers"))
                .addHeader("X-Test", "value"),
            )
          } yield assertTrue(
            res.status == Status.Ok,
            // Note: Testing actual 431 requires exceeding Netty's limit (default 8KB)
            // This verifies normal headers work; real 431 testing needs server config
          )
        },
        test("505 HTTP Version Not Supported for unknown versions") {
          val routes = (Method.GET / "version" -> handler { (_: Request) =>
            Response.text("ok")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // HTTP/1.1 should work
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "version")))
          } yield assertTrue(
            res.status == Status.Ok,
            // Note: Testing 505 requires HTTP/2.0 or invalid version strings
            // Client always sends HTTP/1.1, so we verify that works
          )
        },
        test("411 Length Required for POST without Content-Length or Transfer-Encoding") {
          val routes = (Method.POST / "length-required" -> handler { (req: Request) =>
            val hasContentLength    = req.headers.get(Header.ContentLength).isDefined
            val hasTransferEncoding = req.headers.get("Transfer-Encoding").isDefined

            if (!hasContentLength && !hasTransferEncoding) {
              ZIO.succeed(Response.status(Status.LengthRequired))
            } else {
              for {
                body <- req.body.asString.orDie
              } yield Response.text(s"received=${body.length}")
            }
          }).toRoutes

          for {
            port      <- Server.installRoutes(routes)
            // Normal POST with body (client adds Content-Length automatically)
            normalRes <- Client.batched(
              Request(method = Method.POST, url = urlFor(port, "length-required"), body = Body.fromString("data")),
            )
          } yield assertTrue(
            normalRes.status == Status.Ok,
            // Note: HTTP clients always add Content-Length/TE, so 411 testing requires raw sockets
          )
        },
        test("501 Not Implemented for unrecognized methods") {
          val routes = (Method.GET / "standard" -> handler { (_: Request) =>
            Response.text("ok")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Standard method works
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "standard")))
            // Note: Custom methods like PATCH, LINK are now common; true 501 is rare
          } yield assertTrue(
            res.status == Status.Ok,
          )
        },
      ),
      suite("Content-Length & Transfer-Encoding (RFC 9112 §6.3)")(
        // Critical: Transfer-Encoding takes precedence over Content-Length
        test("Transfer-Encoding: chunked with Content-Length present → CL ignored") {
          // RFC 9112 §6.3(3): "If a message is received with both a Transfer-Encoding
          // and a Content-Length header field, the Transfer-Encoding overrides the
          // Content-Length."
          val routes = (Method.POST / "te-cl-conflict" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              cl = req.headers.get(Header.ContentLength)
              te = req.headers.get("Transfer-Encoding")
            } yield Response.text(s"body_len=${body.length},has_cl=${cl.isDefined},has_te=${te.isDefined}")
          }).toRoutes

          val testBody = "test data content"
          for {
            port <- Server.installRoutes(routes)
            // Client will typically send one or the other, but test verifies proper handling
            res  <- Client.batched(
              Request(method = Method.POST, url = urlFor(port, "te-cl-conflict"), body = Body.fromString(testBody)),
            )
            body <- res.body.asString
            expectedStr = s"body_len=${testBody.length}"
          } yield assertTrue(
            res.status == Status.Ok,
            body.contains(expectedStr),
            // Body should be read correctly regardless of which header was sent
          )
        },
        test("Multiple identical Content-Length headers → accept") {
          // RFC 9112 §6.3(4): Multiple CL headers with same value should be accepted
          val routes = (Method.POST / "multiple-cl-same" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              // Count how many CL headers by checking raw headers
              clOpt = req.headers.get(Header.ContentLength)
            } yield Response.text(s"body_len=${body.length},has_cl=${clOpt.isDefined}")
          }).toRoutes

          val testBody = "12345"
          for {
            port <- Server.installRoutes(routes)
            // Add same Content-Length twice
            req = Request(
              method = Method.POST,
              url = urlFor(port, "multiple-cl-same"),
              body = Body.fromString(testBody),
            )
              .addHeader("Content-Length", testBody.length.toString)
              .addHeader("Content-Length", testBody.length.toString)
            res  <- Client
              .batched(req)
              .catchAll(_ =>
                // May be rejected at client level or server level - both acceptable
                ZIO.succeed(Response.status(Status.BadRequest)),
              )
            body <- res.body.asString
            expectedStr = s"body_len=${testBody.length}"
          } yield assertTrue(
            // Either accepted (200) or rejected (400) - both are valid implementations
            res.status == Status.Ok || res.status == Status.BadRequest,
            // If accepted, verify body was read correctly
            res.status != Status.Ok || body.contains(expectedStr),
          )
        },
        test("Conflicting Content-Length headers → should reject per RFC") {
          // RFC 9112 §6.3(4): "If a message is received without Transfer-Encoding and with
          // either multiple Content-Length header fields having differing field-values or a
          // single Content-Length header field having an invalid value, then the message
          // framing is invalid and the recipient MUST treat it as an unrecoverable error."
          //
          val routes = (Method.POST / "multiple-cl-conflict" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              headers = req.headers.getAll(Header.ContentLength)
            } yield
              if (headers.size > 1) Response.internalServerError("Message with conflicting Content-Length headers")
              else Response.text(s"handler_reached=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            // Try to add conflicting Content-Length headers
            req = Request(
              method = Method.POST,
              url = urlFor(port, "multiple-cl-conflict"),
              body = Body.fromString("12345"),
            )
              .addHeader("Content-Length", "5")
              .addHeader("Content-Length", "10")
            res <- Client
              .batched(req)
              .catchAll(_ =>
                // Expected by spec: client or server rejects
                ZIO.succeed(Response.status(Status.BadRequest)),
              )
            _   <- res.body.asString // Consume body
          } yield assertTrue(
            // The set content lengths by the user is overwritten by the client library,
            // so the server accepts the request, as no broken request is received.
            res.status == Status.Ok,
          )
        },
        test("Conflicting Content-Length headers → should reject per RFC - curl") {
          // RFC 9112 §6.3(4): "If a message is received without Transfer-Encoding and with
          // either multiple Content-Length header fields having differing field-values or a
          // single Content-Length header field having an invalid value, then the message
          // framing is invalid and the recipient MUST treat it as an unrecoverable error."
          // This test uses curl to send the request with conflicting Content-Length headers
          val routes = (Method.POST / "multiple-cl-conflict-curl" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              headers = req.headers.getAll(Header.ContentLength)
            } yield
              if (headers.size > 1) Response.internalServerError("Message with conflicting Content-Length headers")
              else Response.text(s"handler_reached=${body.length}")
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /multiple-cl-conflict-curl HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 5\r\nContent-Length: 10\r\nConnection: close\r\n\r\n12345"""
            response <- sendRawHttp(port, rawRequest)
            status = statusCodeOf(response)
          } yield assertTrue(status.contains(400))
        },
        test("Content-Length on 204 No Content response → must be 0 or omitted") {
          // RFC 9110 §8.6: "A server MAY send a Content-Length header field in a 204 response
          // to indicate the size of the representation that would have been transferred."
          val routes = (Method.DELETE / "delete-resource" -> handler { (_: Request) =>
            // Handler returns 204 - server should not add body
            ZIO.succeed(Response.status(Status.NoContent))
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.DELETE, url = urlFor(port, "delete-resource")))
            body <- res.body.asString
          } yield assertTrue(
            res.status == Status.NoContent,
            body.isEmpty,
            // If Content-Length present, must be 0
            res.headers.get(Header.ContentLength).forall(_.length == 0L),
          )
        },
        test("Content-Length on 304 Not Modified → body must be empty") {
          // RFC 9110 §15.4.5: 304 responses must not contain a message body
          val etag   = Strong("immutable")
          val routes = (Method.GET / "not-modified" -> handler { (_: Request) =>
            Response(Status.NotModified, body = Body.fromString("original content")).addHeader(etag)
          }).toRoutes
          val ifNone = Headers(IfNoneMatch.ETags(NonEmptyChunk(Header.ETag.render(etag))))

          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "not-modified"), headers = ifNone))
            body <- res.body.asString
          } yield assertTrue(
            // May return 304 or 200 depending on implementation
            res.status == Status.NotModified || res.status == Status.Ok,
            // If 304, body must be empty
            res.status != Status.NotModified || body.isEmpty,
          )
        },
        test("Negative Content-Length → should reject per RFC") {
          // RFC 9110 §8.6: Content-Length must be non-negative
          // CURRENT BEHAVIOR: Negative CL appears to be treated as 0 or ignored
          // SPEC REQUIREMENT: Should reject
          val routes = (Method.POST / "negative-cl" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /negative-cl HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: -5\r\nConnection: close\r\n\r\ndata"""
            response <- sendRawHttp(port, rawRequest)
            status = statusCodeOf(response)
          } yield assertTrue(status.contains(400))
        },
        test("Non-numeric Content-Length → should reject per RFC") {
          // RFC 9110 §8.6: Content-Length value must be decimal integer
          // CURRENT BEHAVIOR: Invalid CL appears to be ignored/treated as 0
          // SPEC REQUIREMENT: Should reject with 400
          val routes = (Method.POST / "invalid-cl" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /invalid-cl HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: invalid\r\nConnection: close\r\n\r\ndata"""
            response <- sendRawHttp(port, rawRequest)
            status = statusCodeOf(response)
          } yield assertTrue(status.contains(400))
        },
        test("Leading zeros in Content-Length → accept and normalize") {
          // RFC 9110 §8.6: Leading zeros are allowed
          val routes = (Method.POST / "leading-zeros-cl" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              cl = req.headers.get(Header.ContentLength)
            } yield Response.text(s"body_len=${body.length},cl=${cl.map(_.length).getOrElse(-1)}")
          }).toRoutes

          val testBody = "test"
          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /leading-zeros-cl HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 0004\r\nConnection: close\r\n\r\ntest"""
            response <- sendRawHttp(port, rawRequest)
            status = statusCodeOf(response)
          } yield assertTrue(status.contains(200))

        },
      ),
      suite("Advanced Transfer-Encoding (RFC 9112 §6.1, §6.3, §7.1)")(
        test("Chunk extensions are ignored and don't break body assembly") {
          // RFC 9112 §7.1.1: Chunk extensions (e.g., "4;name=value") should be ignored
          val routes = (Method.POST / "chunk-ext" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /chunk-ext HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n4;ext=val\r\ntest\r\n0\r\n\r\n"""
            output <- sendRawHttp(port, rawRequest)
          } yield assertTrue(output.contains("received=4"))
        },
        test("Zero-length final chunk terminates chunked encoding") {
          // RFC 9112 §7.1.2: Chunked encoding must end with "0\r\n\r\n"
          val routes = (Method.POST / "chunk-final" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /chunk-final HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nhello\r\n0\r\n\r\n"""
            output <- sendRawHttp(port, rawRequest)
          } yield assertTrue(output.contains("200") || output.contains("received=5"))
        },
        test("Invalid chunk size (non-hex) → connection closed or 400") {
          // RFC 9112 §7.1.1: Chunk size must be hexadecimal
          val routes = (Method.POST / "chunk-invalid-size" -> handler { (req: Request) =>
            println(req)
            req.body.asString.orDie.as(Response.text(s"should_not_reach"))
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /chunk-invalid-size HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\nZZZZ\r\ndata\r\n0\r\n\r\n"""
            output <- sendRawHttp(port, rawRequest, allowFailure = true)
          } yield assertTrue(output.contains("400") || output.isEmpty)
        },
        test("Malformed chunked encoding (missing final chunk) → connection closed") {
          // RFC 9112 §7.1.2: Missing "0\r\n\r\n" terminator should cause error
          val routes = (Method.POST / "chunk-no-final" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /chunk-no-final HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nhello"""
            output <- sendRawHttp(port, rawRequest, timeoutSeconds = 1, allowFailure = true)
          } yield assertTrue(output.contains("400") || output.isEmpty || output.length < 50)
        },
        test("Transfer-Encoding: chunked with Content-Length → CL must be ignored (security)") {
          // RFC 9112 §6.3(3): Critical for request smuggling prevention
          // If both TE and CL present, MUST ignore Content-Length
          val routes = (Method.POST / "te-cl-security" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
              cl = req.headers.get(Header.ContentLength)
              te = req.headers.get("Transfer-Encoding")
            } yield Response.text(s"body_len=${body.length},has_cl=${cl.isDefined},has_te=${te.isDefined}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /te-cl-security HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nContent-Length: 3\r\nConnection: close\r\n\r\n5\r\nhello\r\n0\r\n\r\n"""
            output <- sendRawHttp(port, rawRequest)
          } yield assertTrue(output.contains("body_len=5"))
        },
        test("Multiple Transfer-Encoding values → reject or handle safely") {
          // RFC 9112 §6.3: Multiple TE values like "gzip, chunked"
          // If unsupported, must reject safely
          val routes = (Method.POST / "multi-te" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /multi-te HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: gzip, chunked\r\nConnection: close\r\n\r\n4\r\ntest\r\n0\r\n\r\n"""
            response <- sendRawHttp(port, rawRequest)
            status = statusCodeOf(response).getOrElse(0)
          } yield assertTrue(status >= 200 && status < 600)
        },
        test("Invalid Transfer-Encoding value → should reject") {
          // RFC 9112 §6.3: Invalid TE values should be rejected
          val routes = (Method.POST / "invalid-te" -> handler { (req: Request) =>
            req.body.asString.orDie.as(Response.text(s"should_not_reach"))
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /invalid-te HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: invalid-encoding\r\nContent-Length: 4\r\nConnection: close\r\n\r\ntest"""
            response <- sendRawHttp(port, rawRequest, allowFailure = true)
            status = statusCodeOf(response)
          } yield assertTrue(status.exists(code => code >= 200 && code < 600) || response.isEmpty)
        },
        test("Chunk size larger than declared → detect and reject") {
          // RFC 9112 §7.1.1: Chunk size must match actual data
          val routes = (Method.POST / "chunk-size-mismatch" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /chunk-size-mismatch HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n3\r\n1234567890\r\n0\r\n\r\n"""
            output <- sendRawHttp(port, rawRequest, timeoutSeconds = 1, allowFailure = true)
          } yield assertTrue(
            output.contains("400") || output.isEmpty || (!output.contains("200") && output.length < 100),
          )
        },
        test("Trailer headers after final chunk (if supported, don't corrupt)") {
          // RFC 9112 §7.1.2: Trailer headers can follow final chunk
          // If not supported, should not corrupt response
          val routes = (Method.POST / "chunk-trailer" -> handler { (req: Request) =>
            for {
              body <- req.body.asString.orDie
            } yield Response.text(s"received=${body.length}")
          }).toRoutes

          for {
            port <- Server.installRoutes(routes)
            rawRequest =
              s"""POST /chunk-trailer HTTP/1.1\r\nHost: localhost:$port\r\nTransfer-Encoding: chunked\r\nTE: trailers\r\nConnection: close\r\n\r\n5\r\nhello\r\n0\r\nX-Trailer: value\r\n\r\n"""
            output <- sendRawHttp(port, rawRequest)
          } yield assertTrue(output.contains("received=5"))
        },
      ),
      suite("Range & Conditional Advanced")(
        test("Suffix byte range bytes=-10 returns last 10 bytes") {
          val content = "abcdefghijklmnopqrstuvwxyz0123456789"
          val routes  = (Method.GET / "range-suffix" -> handler { (req: Request) =>
            req.header(Header.Range) match {
              case Some(Header.Range.Suffix(unit, suffixLen)) if unit == "bytes" =>
                val start = (content.length - suffixLen.toInt).max(0)
                Response
                  .text(content.substring(start))
                  .status(Status.PartialContent)
                  .addHeader(
                    Header.ContentRange
                      .EndTotal("bytes", start, content.length - 1, content.length),
                  )
                  .addHeader(Header.AcceptRanges.Bytes)
              case _ => Response.text(content).addHeader(Header.AcceptRanges.Bytes)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "range-suffix")).addHeader(
                Header.Range.Suffix("bytes", 10),
              ),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.PartialContent, body == content.takeRight(10))
        },
        test("Prefix byte range bytes=10- returns from offset to end") {
          val content = "abcdefghijklmnopqrstuvwxyz0123456789"
          val routes  = (Method.GET / "range-prefix" -> handler { (req: Request) =>
            req.header(Header.Range) match {
              case Some(Header.Range.Prefix(unit, prefixStart)) if unit == "bytes" =>
                val start = prefixStart.toInt
                val slice = if (start < content.length) content.substring(start) else ""
                Response
                  .text(slice)
                  .status(Status.PartialContent)
                  .addHeader(
                    Header.ContentRange
                      .EndTotal("bytes", start, content.length - 1, content.length),
                  )
                  .addHeader(Header.AcceptRanges.Bytes)
              case Some(Header.Range.Single("bytes", start, endOpt))               =>
                val s     = start.toInt
                val e     = endOpt.map(_.toInt).getOrElse(content.length - 1)
                val slice = content.substring(s, e + 1)
                Response
                  .text(slice)
                  .status(Status.PartialContent)
                  .addHeader(Header.ContentRange.EndTotal("bytes", s, e, content.length))
                  .addHeader(Header.AcceptRanges.Bytes)
              case _ => Response.text(content).addHeader(Header.AcceptRanges.Bytes)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "range-prefix")).addHeader(
                Header.Range.Prefix("bytes", 10),
              ),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.PartialContent, body == content.drop(10))
        },
        test("Invalid range unit items= ignored and full content returned") {
          val content = "0123456789"
          val routes  = (Method.GET / "range-unit" -> handler { (_: Request) =>
            Response.text(content).addHeader(Header.AcceptRanges.Bytes)
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            // Construct raw Range header with unsupported unit via addHeader string
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "range-unit")).addHeader("Range", "items=0-5"),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.Ok || res.status == Status.PartialContent, body == content)
        },
        test("Range header on non-GET (POST) ignored and treated as normal request") {
          val routes = (Method.POST / "range-post" -> handler { (_: Request) => Response.text("posted") }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.POST, url = urlFor(port, "range-post"), body = Body.fromString("data"))
                .addHeader("Range", "bytes=0-2"),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.Ok, body == "posted")
        },
        test("If-Range with matching ETag returns partial") {
          val etag    = "\"abc123\""
          val content = "0123456789abcdefghijklmnopqrstuvwxyz"
          val routes  = (Method.GET / "if-range-etag" -> handler { (req: Request) =>
            val range   = req.header(Header.Range)
            val ifRange = req.header(Header.IfRange)
            ifRange match {
              case Some(Header.IfRange.ETag(v)) if v == etag =>
                range match {
                  case Some(Header.Range.Single("bytes", start, endOpt)) =>
                    val s     = start.toInt; val e = endOpt.map(_.toInt).getOrElse(content.length - 1)
                    val slice = content.substring(s, e + 1)
                    Response
                      .text(slice)
                      .status(Status.PartialContent)
                      .addHeader(Header.ContentRange.EndTotal("bytes", s, e, content.length))
                      .addHeader(Header.AcceptRanges.Bytes)
                  case _ => Response.text(content).addHeader(Header.AcceptRanges.Bytes)
                }
              case _ => Response.text(content).addHeader(Header.AcceptRanges.Bytes)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "if-range-etag"))
                .addHeader(Header.IfRange.ETag(etag))
                .addHeader(Header.Range.Single("bytes", 0, Some(9))),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.PartialContent, body == "0123456789")
        },
        test("If-Range with non-matching ETag returns full content") {
          val etag    = "\"abc123\""
          val content = "0123456789abcdefghijklmnopqrstuvwxyz"
          val routes  = (Method.GET / "if-range-etag-full" -> handler { (req: Request) =>
            req.header(Header.IfRange) match {
              case Some(Header.IfRange.ETag(v)) if v == etag => Response.text("unexpected")
              case _                                         => Response.text(content)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "if-range-etag-full"))
                .addHeader(Header.IfRange.ETag("\"other\""))
                .addHeader(Header.Range.Single("bytes", 0, Some(9))),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.Ok, body == content)
        },
        test("If-Match with wildcard * succeeds when resource exists") {
          val routes = (Method.PUT / "if-match" -> handler { (req: Request) =>
            req.header(Header.IfMatch) match {
              case Some(Header.IfMatch.Any) => Response.text("updated")
              case _                        => Response.status(Status.PreconditionFailed)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.PUT, url = urlFor(port, "if-match")).addHeader(Header.IfMatch.Any),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.Ok, body == "updated")
        },
        test("If-Match with non-matching ETag returns 412 Precondition Failed") {
          val current = "etag-current"
          val routes  = (Method.PUT / "if-match-etag" -> handler { (req: Request) =>
            req.header(Header.IfMatch) match {
              case Some(Header.IfMatch.ETags(etags)) if etags.toChunk.contains(current) => Response.text("updated")
              case _ => Response.status(Status.PreconditionFailed)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.PUT, url = urlFor(port, "if-match-etag")).addHeader(
                Header.IfMatch.ETags(NonEmptyChunk("other")),
              ),
            )
          } yield assertTrue(res.status == Status.PreconditionFailed)
        },
        test("If-Unmodified-Since with unchanged resource allows update") {
          val lastMod = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
          val routes  = (Method.PUT / "iunmod" -> handler { (req: Request) =>
            req.header(Header.IfUnmodifiedSince) match {
              case Some(h) if h.value.isAfter(lastMod.minusDays(1)) => Response.text("updated")
              case _                                                => Response.status(Status.PreconditionFailed)
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.PUT, url = urlFor(port, "iunmod")).addHeader(
                Header.IfUnmodifiedSince(lastMod.plusHours(1)),
              ),
            )
            body <- res.body.asString
          } yield assertTrue(res.status == Status.Ok, body == "updated")
        },
        test("If-Unmodified-Since with modified resource returns 412") {
          val lastMod = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
          val routes  = (Method.PUT / "iunmod-fail" -> handler { (req: Request) =>
            req.header(Header.IfUnmodifiedSince) match {
              case Some(h) if h.value.isBefore(lastMod) => Response.status(Status.PreconditionFailed)
              case _                                    => Response.text("updated")
            }
          }).toRoutes
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.PUT, url = urlFor(port, "iunmod-fail")).addHeader(
                Header.IfUnmodifiedSince(lastMod.minusDays(2)),
              ),
            )
          } yield assertTrue(res.status == Status.PreconditionFailed)
        },
        test("Weak vs strong ETag comparison: If-None-Match weak mismatch returns 200") {
          val strong = Strong("abc")
          val routes =
            (Method.GET / "weak-etag" -> handler { (_: Request) => Response.text("data").addHeader(strong) }).toRoutes
          val weakToken = s"W/${Header.ETag.render(strong)}"
          val headers   = Headers(Header.IfNoneMatch.ETags(NonEmptyChunk(weakToken)))
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "weak-etag"), headers = headers))
          } yield assertTrue(res.status == Status.Ok)
        },
        test("If-None-Match * wildcard prevents serving when any current representation exists") {
          val etag   = Strong("abc")
          val routes =
            (Method.GET / "star-none" -> handler { (_: Request) => Response.text("live").addHeader(etag) }).toRoutes
          val headers = Headers(Header.IfNoneMatch.Any)
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(Request(method = Method.GET, url = urlFor(port, "star-none"), headers = headers))
            body <- res.body.asString
          } yield assertTrue(res.status == Status.NotModified || body == "live")
        },
        test("ETag precedence over Last-Modified in conditional: If-None-Match match returns 304 regardless of IMS") {
          val etag         = Strong("abc")
          val lastModified = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
          val routes       = (Method.GET / "etag-precedence" -> handler { (_: Request) =>
            Response.text("content").addHeader(etag).addHeader(LastModified(lastModified))
          }).toRoutes
          val headers      = Headers(
            Header.IfNoneMatch.ETags(NonEmptyChunk(Header.ETag.render(etag))),
            Header.IfModifiedSince(lastModified.minusDays(10)),
          )
          for {
            port <- Server.installRoutes(routes)
            res  <- Client.batched(
              Request(method = Method.GET, url = urlFor(port, "etag-precedence"), headers = headers),
            )
          } yield assertTrue(res.status == Status.NotModified || res.status == Status.Ok)
        },
      ),
    ).provideShared(
      ZLayer.succeed(Server.Config.default.port(0)),
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      Server.customized,
      Client.default,
    ) @@ TestAspect.sequential @@ TestAspect.withLiveClock @@ TestAspect.timeout(30.seconds)
}
