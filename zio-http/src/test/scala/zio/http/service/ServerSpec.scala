package zio.http.service

import io.netty.util.AsciiString
import zio.http._
import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zio.http.model._
import zio.stream.{ZPipeline, ZStream}
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Chunk, Scope, ZIO, durationInt}

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object ServerSpec extends HttpRunnableSpec {
  import html._

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyBody(Gen.const(data))
  } yield (data.mkString(""), content)

  private val MaxSize = 1024 * 10
  val configApp       = ServerConfig.default
    .requestDecompression(true)
    .objectAggregator(MaxSize)
    .responseCompression()

  private val app = serve(DynamicServer.app)

  def dynamicAppSpec = suite("DynamicAppSpec")(
    suite("success")(
      test("status is 200") {
        val status = Handler.ok.toHttp.deploy.status.run()
        assertZIO(status)(equalTo(Status.Ok))
      },
      test("status is 200") {
        val res = Handler.text("ABC").toHttp.deploy.status.run()
        assertZIO(res)(equalTo(Status.Ok))
      },
      test("content is set") {
        val res = Handler.text("ABC").toHttp.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(containsString("ABC"))
      },
    ),
    suite("not found") {
      val app = Http.empty
      test("status is 404") {
        val res = app.deploy.status.run()
        assertZIO(res)(equalTo(Status.NotFound))
      } +
        test("header is set") {
          val res = app.deploy.headerValue(HeaderNames.contentLength).run()
          assertZIO(res)(isSome(equalTo("0")))
        }
    } +
      suite("error") {
        val app = Handler.fail(new Error("SERVER_ERROR")).toHttp
        test("status is 500") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is empty") {
            val res = app.deploy.body.mapZIO(_.asString).run()
            assertZIO(res)(isEmptyString)
          } +
          test("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("die") {
        val app = Handler.die(new Error("SERVER_ERROR")).toHttp
        test("status is 500") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is empty") {
            val res = app.deploy.body.mapZIO(_.asString).run()
            assertZIO(res)(isEmptyString)
          } +
          test("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val app = Http.collectZIO[Request] { case req =>
          req.body.asString.map(text => Response.text(text))
        }

        test("status is 200") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.Ok))
        } +
          test("body is ok") {
            val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString("ABC"))
            assertZIO(res)(equalTo("ABC"))
          } +
          test("empty string") {
            val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(""))
            assertZIO(res)(equalTo(""))
          } +
          test("one char") {
            val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString("1"))
            assertZIO(res)(equalTo("1"))
          } +
          test("data") {
            val dataStream = ZStream.repeat("A").take(MaxSize.toLong)
            val app        = Http.collect[Request] { case req => Response(body = req.body) }
            val res        = app.deploy.body.mapZIO(_.asChunk.map(_.length)).run(body = Body.fromStream(dataStream))
            assertZIO(res)(equalTo(MaxSize))
          }
      } +
      suite("headers") {
        val app = Handler.ok.addHeader("Foo", "Bar").toHttp
        test("headers are set") {
          val res = app.deploy.headerValue("Foo").run()
          assertZIO(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = Handler.response(Response(status = Status.Ok, body = Body.fromString("abc"))).toHttp
        test("body is set") {
          val res = app.deploy.body.mapZIO(_.asString).run()
          assertZIO(res)(equalTo("abc"))
        }
      } +
      suite("compression") {
        val body         = "some-text"
        val bodyAsStream = ZStream.fromChunk(Chunk.fromArray(body.getBytes))

        val app = Http.collectZIO[Request] { case req => req.body.asString.map(body => Response.text(body)) }.deploy

        def roundTrip[R, E <: Throwable](
          app: HttpApp[R, Throwable],
          headers: Headers,
          contentStream: ZStream[R, E, Byte],
          compressor: ZPipeline[R, E, Byte, Byte],
          decompressor: ZPipeline[R, E, Byte, Byte],
        ) = for {
          compressed <- contentStream.via(compressor).runCollect
          response   <- app.run(body = Body.fromChunk(compressed), headers = headers)
          body       <- response.body.asChunk.flatMap(ch => ZStream.fromChunk(ch).via(decompressor).runCollect)
        } yield new String(body.toArray, StandardCharsets.UTF_8)

        test("should decompress request and compress response") {
          checkAll(
            Gen.fromIterable(
              List(
                // Content-Encoding,   Client Request Compressor, Accept-Encoding,      Client Response Decompressor
                (HeaderValues.gzip, ZPipeline.gzip(), HeaderValues.gzip, ZPipeline.gunzip()),
                (HeaderValues.deflate, ZPipeline.deflate(), HeaderValues.deflate, ZPipeline.inflate()),
                (HeaderValues.gzip, ZPipeline.gzip(), HeaderValues.deflate, ZPipeline.inflate()),
                (HeaderValues.deflate, ZPipeline.deflate(), HeaderValues.gzip, ZPipeline.gunzip()),
              ),
            ),
          ) { case (contentEncoding, compressor, acceptEncoding, decompressor) =>
            val result = roundTrip(
              app,
              Headers.acceptEncoding(acceptEncoding) ++ Headers.contentEncoding(contentEncoding),
              bodyAsStream,
              compressor,
              decompressor,
            )
            assertZIO(result)(equalTo(body))
          }
        } +
          test("pass through for unsupported accept encoding request") {
            val result = app.run(
              body = Body.fromString(body),
              headers = Headers.acceptEncoding(HeaderValues.br),
            )
            assertZIO(result.flatMap(_.body.asString))(equalTo(body))
          } +
          test("fail on getting compressed response") {
            checkAll(Gen.fromIterable(List(HeaderValues.gzip, HeaderValues.deflate, HeaderValues.gzipDeflate))) { ae =>
              val result = app.run(
                body = Body.fromString(body),
                headers = Headers.acceptEncoding(ae),
              )
              assertZIO(result.flatMap(_.body.asString))(not(equalTo(body)))
            }
          }
      },
  )

  def requestSpec = suite("RequestSpec") {
    val app: HttpApp[Any, Nothing] = Http.collect[Request] { case req =>
      Response.text(req.contentLength.getOrElse(-1).toString)
    }
    test("has content-length") {
      check(Gen.alphaNumericString) { string =>
        val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(string))
        assertZIO(res)(equalTo(string.length.toString))
      }
    } +
      test("POST Request.getBody") {
        val app = Http.collectZIO[Request] { case req => req.body.asChunk.as(Response.ok) }
        val res = app.deploy.status.run(path = !!, method = Method.POST, body = Body.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      } +
      test("body can be read multiple times") {
        val app = Http.collectZIO[Request] { case req => (req.body.asChunk *> req.body.asChunk).as(Response.ok) }
        val res = app.deploy.status.run(method = Method.POST, body = Body.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      }
  }

  def responseSpec = suite("ResponseSpec")(
    test("data") {
      check(nonEmptyContent) { case (string, data) =>
        val res = Handler.fromBody(data).toHttp.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(equalTo(string))
      }
    },
    test("data from file") {
      val res = Http.fromResource("TestFile.txt").deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("foo\nbar"))
    },
    test("content-type header on file response") {
      val res =
        Http
          .fromResource("TestFile2.mp4")
          .deploy
          .headerValue(HeaderNames.contentType)
          .run()
          .map(_.getOrElse("Content type header not found."))
      assertZIO(res)(equalTo("video/mp4"))
    },
    test("status") {
      checkAll(HttpGen.status) { case status =>
        val res = Handler.status(status).toHttp.deploy.status.run()
        assertZIO(res)(equalTo(status))
      }

    },
    test("header") {
      check(HttpGen.header) { header =>
        val res = Handler.ok.addHeader(header).toHttp.deploy.headerValue(header.key).run()
        assertZIO(res)(isSome(equalTo(header.value)))
      }
    },
    test("text streaming") {
      val res = Handler.fromStream(ZStream("a", "b", "c")).toHttp.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("abc"))
    },
    test("echo streaming") {
      val res = Http
        .collectHandler[Request] { case req =>
          Handler.fromStream(ZStream.fromZIO(req.body.asChunk).flattenChunks)
        }
        .deploy
        .body
        .mapZIO(_.asString)
        .run(body = Body.fromString("abc"))
      assertZIO(res)(equalTo("abc"))
    },
    test("file-streaming") {
      val path = getClass.getResource("/TestFile.txt").getPath
      val res  = Handler.fromStream(ZStream.fromPath(Paths.get(path))).toHttp.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("foo\nbar"))
    } @@ TestAspect.os(os => !os.isWindows),
    suite("html")(
      test("body") {
        val res = Handler.html(html(body(div(id := "foo", "bar")))).toHttp.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
      },
      test("content-type") {
        val app = Handler.html(html(body(div(id := "foo", "bar")))).toHttp
        val res = app.deploy.headerValue(HeaderNames.contentType).run()
        assertZIO(res)(isSome(equalTo(HeaderValues.textHtml.toString)))
      },
    ),
    suite("content-length")(
      suite("string") {
        test("unicode text") {
          val res = Handler.text("äöü").toHttp.deploy.contentLength.run()
          assertZIO(res)(isSome(equalTo(6L)))
        } +
          test("already set") {
            val res = Handler.text("1234567890").withContentLength(4L).toHttp.deploy.contentLength.run()
            assertZIO(res)(isSome(equalTo(4L)))
          }
      },
    ),
    suite("memoize")(
      test("concurrent") {
        val size     = 100
        val expected = (0 to size) map (_ => Status.Ok)
        val response = Response.text("abc").freeze
        for {
          actual <- ZIO.foreachPar(0 to size)(_ => Handler.response(response).toHttp.deploy.status.run())
        } yield assert(actual)(equalTo(expected))
      },
      test("update after cache") {
        val server = "ZIO-Http"
        val res    = Response.text("abc").freeze
        for {
          actual <- Handler.response(res).withServer(server).toHttp.deploy.headerValue(HeaderNames.server).run()
        } yield assert(actual)(isSome(equalTo(server)))
      },
      test("multiple headers of same type with different values") {
        val expectedValue = "test1,test2"
        val res           = Response.text("abc").withVary("test1").withVary("test2").freeze

        for {
          actual <- Handler.response(res).toHttp.deploy.headerValue(HeaderNames.vary).run()
        } yield assert(actual)(isSome(equalTo(expectedValue)))
      },
    ),
  )

  def requestBodySpec = suite("RequestBodySpec")(
    test("POST Request stream") {
      val app: Http[Any, Throwable, Request, Response] = Http.collect[Request] { case req =>
        Response(body = Body.fromStream(req.body.asStream))
      }
      check(Gen.alphaNumericString) { c =>
        assertZIO(app.deploy.body.mapZIO(_.asString).run(path = !!, method = Method.POST, body = Body.fromString(c)))(
          equalTo(c),
        )
      }
    },
    test("FromASCIIString: toHttp") {
      check(Gen.asciiString) { payload =>
        val res = Body.fromAsciiString(AsciiString.cached(payload)).asString(HTTP_CHARSET)
        assertZIO(res)(equalTo(payload))
      }
    },
  )

  def serverErrorSpec = suite("ServerErrorSpec") {
    val app = Handler.fail(new Error("SERVER_ERROR")).toHttp
    test("status is 500") {
      val res = app.deploy.status.run()
      assertZIO(res)(equalTo(Status.InternalServerError))
    } +
      test("content is empty") {
        val res = app.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(isEmptyString)
      } +
      test("header is set") {
        val res = app.deploy.headers.run().map(_.headerValue("Content-Length"))
        assertZIO(res)(isSome(anything))
      }
  }

  override def spec =
    suite("Server") {
      val spec = dynamicAppSpec + responseSpec + requestSpec + requestBodySpec + serverErrorSpec
      suite("app without request streaming") { ZIO.scoped(app.as(List(spec))) }
    }.provideSomeShared[TestEnvironment](
      DynamicServer.live,
      ServerConfig.live(configApp),
      Server.live,
      Client.default,
      Scope.default,
    ) @@ timeout(30 seconds) @@ sequential

}
