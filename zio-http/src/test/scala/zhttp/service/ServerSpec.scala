package zhttp.service

import io.netty.util.AsciiString
import zhttp.html._
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zio.stream.{ZPipeline, ZStream}
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Chunk, ZIO, _}

import java.nio.file.Paths

object ServerSpec extends HttpRunnableSpec {

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyHttpData(Gen.const(data))
  } yield (data.mkString(""), content)

  private val env = DynamicServer.live ++ Scope.default

  private val MaxSize             = 1024 * 10
  private val app                 =
    serve(DynamicServer.app, Some(Server.requestDecompression(true) ++ Server.enableObjectAggregator(MaxSize)))
  private val appWithReqStreaming = serve(DynamicServer.app, Some(Server.requestDecompression(true)))

  def dynamicAppSpec = suite("DynamicAppSpec")(
    suite("success")(
      test("status is 200") {
        val status = Http.ok.deploy.status.run()
        assertZIO(status)(equalTo(Status.Ok))
      },
      test("status is 200") {
        val res = Http.text("ABC").deploy.status.run()
        assertZIO(res)(equalTo(Status.Ok))
      },
      test("content is set") {
        val res = Http.text("ABC").deploy.bodyAsString.run()
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
          assertZIO(res)(isSome(equalTo("439")))
        }
    } +
      suite("error") {
        val app = Http.fail(new Error("SERVER_ERROR"))
        test("status is 500") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is set") {
            val res = app.deploy.bodyAsString.run()
            assertZIO(res)(containsString("SERVER_ERROR"))
          } +
          test("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("die") {
        val app = Http.die(new Error("SERVER_ERROR"))
        test("status is 500") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is set") {
            val res = app.deploy.bodyAsString.run()
            assertZIO(res)(containsString("SERVER_ERROR"))
          } +
          test("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val app = Http.collectZIO[Request] { case req =>
          req.bodyAsString.map(text => Response.text(text))
        }

        test("status is 200") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.Ok))
        } +
          test("body is ok") {
            val res = app.deploy.bodyAsString.run(content = HttpData.fromString("ABC"))
            assertZIO(res)(equalTo("ABC"))
          } +
          test("empty string") {
            val res = app.deploy.bodyAsString.run(content = HttpData.fromString(""))
            assertZIO(res)(equalTo(""))
          } +
          test("one char") {
            val res = app.deploy.bodyAsString.run(content = HttpData.fromString("1"))
            assertZIO(res)(equalTo("1"))
          } +
          test("data") {
            val dataStream = ZStream.repeat("A").take(MaxSize.toLong)
            val app        = Http.collect[Request] { case req => Response(data = req.data) }
            val res = app.deploy.bodyAsByteBuf.map(_.readableBytes()).run(content = HttpData.fromStream(dataStream))
            assertZIO(res)(equalTo(MaxSize))
          }
      } +
      suite("headers") {
        val app = Http.ok.addHeader("Foo", "Bar")
        test("headers are set") {
          val res = app.deploy.headerValue("Foo").run()
          assertZIO(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = Http.response(Response(status = Status.Ok, data = HttpData.fromString("abc")))
        test("body is set") {
          val res = app.deploy.bodyAsString.run()
          assertZIO(res)(equalTo("abc"))
        }
      } +
      suite("decompression") {
        val app     = Http.collectZIO[Request] { case req => req.bodyAsString.map(body => Response.text(body)) }.deploy
        val content = "some-text"
        val stream  = ZStream.fromChunk(Chunk.fromArray(content.getBytes))

        test("gzip") {
          val res = for {
            body     <- stream.via(ZPipeline.gzip()).runCollect
            response <- app.run(
              content = HttpData.fromChunk(body),
              headers = Headers.contentEncoding(HeaderValues.gzip),
            )
          } yield response
          assertZIO(res.flatMap(_.bodyAsString))(equalTo(content))
        } +
          test("deflate") {
            val res = for {
              body     <- stream.via(ZPipeline.deflate()).runCollect
              response <- app.run(
                content = HttpData.fromChunk(body),
                headers = Headers.contentEncoding(HeaderValues.deflate),
              )
            } yield response
            assertZIO(res.flatMap(_.bodyAsString))(equalTo(content))
          }
      },
  )

  def requestSpec = suite("RequestSpec") {
    val app: HttpApp[Any, Nothing] = Http.collect[Request] { case req =>
      Response.text(req.contentLength.getOrElse(-1).toString)
    }
    test("has content-length") {
      check(Gen.alphaNumericString) { string =>
        val res = app.deploy.bodyAsString.run(content = HttpData.fromString(string))
        assertZIO(res)(equalTo(string.length.toString))
      }
    } +
      test("POST Request.getBody") {
        val app = Http.collectZIO[Request] { case req => req.body.as(Response.ok) }
        val res = app.deploy.status.run(path = !!, method = Method.POST, content = HttpData.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      }
  }

  def responseSpec = suite("ResponseSpec")(
    test("data") {
      check(nonEmptyContent) { case (string, data) =>
        val res = Http.fromData(data).deploy.bodyAsString.run()
        assertZIO(res)(equalTo(string))
      }
    },
    test("data from file") {
      val res = Http.fromResource("TestFile.txt").deploy.bodyAsString.run()
      assertZIO(res)(equalTo("abc\nfoo"))
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
        val res = Http.status(status).deploy.status.run()
        assertZIO(res)(equalTo(status))
      }

    },
    test("header") {
      check(HttpGen.header) { case header @ (name, value) =>
        val res = Http.ok.addHeader(header).deploy.headerValue(name).run()
        assertZIO(res)(isSome(equalTo(value)))
      }
    },
    test("text streaming") {
      val res = Http.fromStream(ZStream("a", "b", "c")).deploy.bodyAsString.run()
      assertZIO(res)(equalTo("abc"))
    },
    test("echo streaming") {
      val res = Http
        .collectHttp[Request] { case req =>
          Http.fromStream(ZStream.fromZIO(req.body).flattenChunks)
        }
        .deploy
        .bodyAsString
        .run(content = HttpData.fromString("abc"))
      assertZIO(res)(equalTo("abc"))
    },
    test("file-streaming") {
      val path = getClass.getResource("/TestFile.txt").getPath
      val res  = Http.fromStream(ZStream.fromPath(Paths.get(path))).deploy.bodyAsString.run()
      assertZIO(res)(equalTo("abc\nfoo"))
    },
    suite("html")(
      test("body") {
        val res = Http.html(html(body(div(id := "foo", "bar")))).deploy.bodyAsString.run()
        assertZIO(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
      },
      test("content-type") {
        val app = Http.html(html(body(div(id := "foo", "bar"))))
        val res = app.deploy.headerValue(HeaderNames.contentType).run()
        assertZIO(res)(isSome(equalTo(HeaderValues.textHtml.toString)))
      },
    ),
    suite("content-length")(
      suite("string") {
        test("unicode text") {
          val res = Http.text("äöü").deploy.contentLength.run()
          assertZIO(res)(isSome(equalTo(6L)))
        } +
          test("already set") {
            val res = Http.text("1234567890").withContentLength(4L).deploy.contentLength.run()
            assertZIO(res)(isSome(equalTo(4L)))
          }
      },
    ),
    suite("memoize")(
      test("concurrent") {
        val size     = 100
        val expected = (0 to size) map (_ => Status.Ok)
        for {
          response <- Response.text("abc").freeze
          actual   <- ZIO.foreachPar(0 to size)(_ => Http.response(response).deploy.status.run())
        } yield assert(actual)(equalTo(expected))
      },
      test("update after cache") {
        val server = "ZIO-Http"
        for {
          res    <- Response.text("abc").freeze
          actual <- Http.response(res).withServer(server).deploy.headerValue(HeaderNames.server).run()
        } yield assert(actual)(isSome(equalTo(server)))
      },
    ),
  )

  def requestBodySpec = suite("RequestBodySpec")(
    test("POST Request stream") {
      val app: Http[Any, Throwable, Request, Response] = Http.collect[Request] { case req =>
        Response(data = HttpData.fromStream(req.bodyAsStream))
      }
      check(Gen.alphaNumericString) { c =>
        assertZIO(app.deploy.bodyAsString.run(path = !!, method = Method.POST, content = HttpData.fromString(c)))(
          equalTo(c),
        )
      }
    },
    test("FromASCIIString: toHttp") {
      check(Gen.asciiString) { payload =>
        val res = HttpData.fromAsciiString(AsciiString.cached(payload)).toHttp.map(_.toString(HTTP_CHARSET))
        assertZIO(res.run())(equalTo(payload))
      }
    },
  )

  def serverErrorSpec = suite("ServerErrorSpec") {
    val app = Http.fail(new Error("SERVER_ERROR"))
    test("status is 500") {
      val res = app.deploy.status.run()
      assertZIO(res)(equalTo(Status.InternalServerError))
    } +
      test("content is set") {
        val res = app.deploy.bodyAsString.run()
        assertZIO(res)(containsString("SERVER_ERROR"))
      } +
      test("header is set") {
        val res = app.deploy.headers.run().map(_.headerValue("Content-Length"))
        assertZIO(res)(isSome(anything))
      }
  }

  override def spec =
    suite("Server") {
      val spec = dynamicAppSpec + responseSpec + requestSpec + requestBodySpec + serverErrorSpec
      suite("app without request streaming") { ZIO.scoped(app.as(List(spec))) } +
        suite("app with request streaming") { ZIO.scoped(appWithReqStreaming.as(List(spec))) }
    }.provideSomeLayerShared[TestEnvironment](env) @@ timeout(30 seconds) @@ sequential

}
