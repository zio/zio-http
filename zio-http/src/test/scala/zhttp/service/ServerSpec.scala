package zhttp.service

import zhttp.html._
import zhttp.http._
import zhttp.internal.HttpRunnableSpec.HttpTestClient
import zhttp.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.service.server.content.compression.CompressionOptions.{deflate, gzip}
import zhttp.service.server.content.compression.CompressionOptions.{deflate, gzip}
import zio.duration.durationInt
import zio.stream.{ZStream, ZTransducer}
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Chunk, ZIO}

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object ServerSpec extends HttpRunnableSpec {

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyHttpData(Gen.const(data))
  } yield (data.mkString(""), content)

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  // contentSizeThreshold set to 0 will compress all responses
  val server = Server.requestDecompression(true) ++ Server.responseCompression(0, IndexedSeq(gzip, deflate)) ++ Server.enableObjectAggregator(4096)
  private val app                 =
    serve(DynamicServer.app, Some(server))
  private val appWithReqStreaming = serve(DynamicServer.app)

  def dynamicAppSpec = suite("DynamicAppSpec") {
    suite("success") {
      testM("status is 200") {
        val status = Http.ok.deploy.status.run()
        assertM(status)(equalTo(Status.Ok))
      } +
        testM("status is 200") {
          val res = Http.text("ABC").deploy.status.run()
          assertM(res)(equalTo(Status.Ok))
        } +
        testM("content is set") {
          val res = Http.text("ABC").deploy.bodyAsString.run()
          assertM(res)(containsString("ABC"))
        }
    } +
      suite("not found") {
        val app = Http.empty
        testM("status is 404") {
          val res = app.deploy.status.run()
          assertM(res)(equalTo(Status.NotFound))
        } +
          testM("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertM(res)(isSome(equalTo("0")))
          }
      } +
      suite("error") {
        val app = Http.fail(new Error("SERVER_ERROR"))
        testM("status is 500") {
          val res = app.deploy.status.run()
          assertM(res)(equalTo(Status.InternalServerError))
        } +
          testM("content is set") {
            val res = app.deploy.bodyAsString.run()
            assertM(res)(containsString("SERVER_ERROR"))
          } +
          testM("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertM(res)(isSome(anything))
          }
      } +
      suite("die") {
        val app = Http.die(new Error("SERVER_ERROR"))
        testM("status is 500") {
          val res = app.deploy.status.run()
          assertM(res)(equalTo(Status.InternalServerError))
        } +
          testM("content is set") {
            val res = app.deploy.bodyAsString.run()
            assertM(res)(containsString("SERVER_ERROR"))
          } +
          testM("header is set") {
            val res = app.deploy.headerValue(HeaderNames.contentLength).run()
            assertM(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val app = Http.collectZIO[Request] { case req =>
          req.bodyAsString.map(text => Response.text(text))
        }

        testM("status is 200") {
          val res = app.deploy.status.run()
          assertM(res)(equalTo(Status.Ok))
        } +
          testM("body is ok") {
            val res = app.deploy.bodyAsString.run(content = HttpData.fromString("ABC"))
            assertM(res)(equalTo("ABC"))
          } +
          testM("empty string") {
            val res = app.deploy.bodyAsString.run(content = HttpData.fromString(""))
            assertM(res)(equalTo(""))
          } +
          testM("one char") {
            val res = app.deploy.bodyAsString.run(content = HttpData.fromString("1"))
            assertM(res)(equalTo("1"))
          }
      } +
      suite("headers") {
        val app = Http.ok.addHeader("Foo", "Bar")
        testM("headers are set") {
          val res = app.deploy.headerValue("Foo").run()
          assertM(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = Http.response(Response(status = Status.Ok, data = HttpData.fromString("abc")))
        testM("body is set") {
          val res = app.deploy.bodyAsString.run()
          assertM(res)(equalTo("abc"))
        }
      } +
      suite("compression") {
        def roundTrip[R, E](
          app: HttpTestClient[Any, Client.ClientRequest, Client.ClientResponse],
          headers: Headers,
          contentStream: ZStream[R, E, Byte],
          compressor: ZTransducer[R, Nothing, Byte, Byte],
          decompressor: ZTransducer[R, E, Byte, Byte],
        ) = for {
          compressed <- contentStream.transduce(compressor).runCollect
          response   <- app.run(content = HttpData.fromChunk(compressed), headers = headers)
          body       <- response.body.flatMap(c => ZStream.fromChunk(c).transduce(decompressor).runCollect)
        } yield new String(body.toArray, StandardCharsets.UTF_8)

        val content = "some-text"
        val stream  = ZStream.fromChunk(Chunk.fromArray(content.getBytes))
        val app     = Http.collectZIO[Request] { case req => req.bodyAsString.map(body => Response.text(body)) }.deploy

        testM("should decompress gzip and compress gzip") {
          val headers = Headers.contentEncoding(HeaderValues.gzip) ++ Headers.acceptEncoding(HeaderValues.gzip)
          val result  = roundTrip(app, headers, stream, ZTransducer.gzip(), ZTransducer.gunzip())

          assertM(result)(equalTo(content))
        } +
          testM("should decompress deflate and compress deflate") {
            val headers = Headers.contentEncoding(HeaderValues.deflate) ++ Headers.acceptEncoding(HeaderValues.deflate)
            val result  = roundTrip(app, headers, stream, ZTransducer.deflate(), ZTransducer.inflate())

            assertM(result)(equalTo(content))
          } +
          testM("should decompress gzip and compress deflate") {
            val headers = Headers.contentEncoding(HeaderValues.gzip) ++ Headers.acceptEncoding(HeaderValues.deflate)
            val result  = roundTrip(app, headers, stream, ZTransducer.gzip(), ZTransducer.inflate())

            assertM(result)(equalTo(content))
          } +
          testM("should decompress deflate and compress gzip") {
            val headers = Headers.contentEncoding(HeaderValues.deflate) ++ Headers.acceptEncoding(HeaderValues.gzip)
            val result  = roundTrip(app, headers, stream, ZTransducer.deflate(), ZTransducer.gunzip())

            assertM(result)(equalTo(content))
          }
      }
  }

  def requestSpec = suite("RequestSpec") {

    val app: HttpApp[Any, Nothing] = Http.collect[Request] { case req =>
      Response.text(req.contentLength.getOrElse(-1).toString)
    }
    testM("has content-length") {
      checkAllM(Gen.alphaNumericString) { string =>
        val res = app.deploy.bodyAsString.run(content = HttpData.fromString(string))
        assertM(res)(equalTo(string.length.toString))
      }
    } +
      testM("POST Request.getBody") {
        val app = Http.collectZIO[Request] { case req => req.body.as(Response.ok) }
        val res = app.deploy.status.run(path = !!, method = Method.POST, content = HttpData.fromString("some text"))
        assertM(res)(equalTo(Status.Ok))
      }
  }

  def responseSpec    = suite("ResponseSpec") {
    testM("data") {
      checkAllM(nonEmptyContent) { case (string, data) =>
        val res = Http.fromData(data).deploy.bodyAsString.run()
        assertM(res)(equalTo(string))
      }
    } +
      testM("data from file") {
        val res = Http.fromResource("TestFile.txt").deploy.bodyAsString.run()
        assertM(res)(equalTo("abc\nfoo"))
      } +
      testM("content-type header on file response") {
        val res =
          Http
            .fromResource("TestFile2.mp4")
            .deploy
            .headerValue(HeaderNames.contentType)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("video/mp4"))
      } +
      testM("status") {
        checkAllM(HttpGen.status) { case status =>
          val res = Http.status(status).deploy.status.run()
          assertM(res)(equalTo(status))
        }

      } +
      testM("header") {
        checkAllM(HttpGen.header) { case header @ (name, value) =>
          val res = Http.ok.addHeader(header).deploy.headerValue(name).run()
          assertM(res)(isSome(equalTo(value)))
        }
      } +
      testM("text streaming") {
        val res = Http.fromStream(ZStream("a", "b", "c")).deploy.bodyAsString.run()
        assertM(res)(equalTo("abc"))
      } +
      testM("echo streaming") {
        val res = Http
          .collectHttp[Request] { case req =>
            Http.fromStream(ZStream.fromEffect(req.body).flattenChunks)
          }
          .deploy
          .bodyAsString
          .run(content = HttpData.fromString("abc"))
        assertM(res)(equalTo("abc"))
      } +
      testM("file-streaming") {
        val path = getClass.getResource("/TestFile.txt").getPath
        val res  = Http.fromStream(ZStream.fromFile(Paths.get(path))).deploy.bodyAsString.run()
        assertM(res)(equalTo("abc\nfoo"))
      } +
      suite("html") {
        testM("body") {
          val res = Http.html(html(body(div(id := "foo", "bar")))).deploy.bodyAsString.run()
          assertM(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
        } +
          testM("content-type") {
            val app = Http.html(html(body(div(id := "foo", "bar"))))
            val res = app.deploy.headerValue(HeaderNames.contentType).run()
            assertM(res)(isSome(equalTo(HeaderValues.textHtml.toString)))
          }
      } +
      suite("content-length") {
        suite("string") {
          testM("unicode text") {
            val res = Http.text("äöü").deploy.contentLength.run()
            assertM(res)(isSome(equalTo(6L)))
          } +
            testM("already set") {
              val res = Http.text("1234567890").withContentLength(4L).deploy.contentLength.run()
              assertM(res)(isSome(equalTo(4L)))
            }
        }
      } +
      suite("memoize") {
        testM("concurrent") {
          val size     = 100
          val expected = (0 to size) map (_ => Status.Ok)
          for {
            response <- Response.text("abc").freeze
            actual   <- ZIO.foreachPar(0 to size)(_ => Http.response(response).deploy.status.run())
          } yield assert(actual)(equalTo(expected))
        } +
          testM("update after cache") {
            val server = "ZIO-Http"
            for {
              res    <- Response.text("abc").freeze
              actual <- Http.response(res).withServer(server).deploy.headerValue(HeaderNames.server).run()
            } yield assert(actual)(isSome(equalTo(server)))
          }
      }
  }
  def requestBodySpec = suite("RequestBodySpec") {
    testM("POST Request stream") {
      val app: Http[Any, Throwable, Request, Response] = Http.collect[Request] { case req =>
        Response(data = HttpData.fromStream(req.bodyAsStream))
      }
      checkAllM(Gen.alphaNumericString) { c =>
        assertM(app.deploy.bodyAsString.run(path = !!, method = Method.POST, content = HttpData.fromString(c)))(
          equalTo(c),
        )
      }
    }
  }

  def serverErrorSpec = suite("ServerErrorSpec") {
    val app = Http.fail(new Error("SERVER_ERROR"))
    testM("status is 500") {
      val res = app.deploy.status.run()
      assertM(res)(equalTo(Status.InternalServerError))
    } +
      testM("content is set") {
        val res = app.deploy.bodyAsString.run()
        assertM(res)(containsString("SERVER_ERROR"))
      } +
      testM("header is set") {
        val res = app.deploy.headers.run().map(_.headerValue("Content-Length"))
        assertM(res)(isSome(anything))
      }
  }

  override def spec =
    suite("Server") {
      val spec = dynamicAppSpec + responseSpec + requestSpec + requestBodySpec + serverErrorSpec
      suiteM("app without request streaming") { app.as(List(spec)).useNow } +
        suiteM("app with request streaming") { appWithReqStreaming.as(List(spec)).useNow }
    }.provideCustomLayerShared(env) @@ timeout(30 seconds) @@ sequential

}
