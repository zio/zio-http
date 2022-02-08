package zhttp.service

import zhttp.html._
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import java.io.File
import java.nio.file.Paths

object ServerSpec extends HttpRunnableSpec {

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyHttpData(Gen.const(data))
  } yield (data.mkString(""), content)

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  private val staticApp = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
    case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
    case Method.GET -> !! / "get%2Fsuccess" => ZIO.succeed(Response.ok)
  }

  // Use this route to test anything that doesn't require ZIO related computations.
  private val nonZIO = Http.collectHttp[Request] {
    case _ -> !! / "HExitSuccess" => Http.ok
    case _ -> !! / "HExitFailure" => Http.fail(new RuntimeException("FAILURE"))
  }

  private val app = serve { nonZIO ++ staticApp ++ DynamicServer.app }

  def dynamicAppSpec = suite("DynamicAppSpec") {
    suite("success") {
      testM("status is 200") {
        val status = Http.ok.deploy.status.run()
        assertM(status)(equalTo(Status.OK))
      } +
        testM("status is 200") {
          val res = Http.text("ABC").deploy.status.run()
          assertM(res)(equalTo(Status.OK))
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
          assertM(res)(equalTo(Status.NOT_FOUND))
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
          assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
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
          assertM(res)(equalTo(Status.OK))
        } +
          testM("body is ok") {
            val res = app.deploy.bodyAsString.run(content = "ABC")
            assertM(res)(equalTo("ABC"))
          } +
          testM("empty string") {
            val res = app.deploy.bodyAsString.run(content = "")
            assertM(res)(equalTo(""))
          } +
          testM("one char") {
            val res = app.deploy.bodyAsString.run(content = "1")
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
        val app = Http.response(Response(status = Status.OK, data = HttpData.fromString("abc")))
        testM("body is set") {
          val res = app.deploy.bodyAsString.run()
          assertM(res)(equalTo("abc"))
        }
      }
  }

  def requestSpec = suite("RequestSpec") {
    val app: HttpApp[Any, Nothing] = Http.collect[Request] { case req =>
      Response.text(req.contentLength.getOrElse(-1).toString)
    }
    testM("has content-length") {
      checkAllM(Gen.alphaNumericString) { string =>
        val res = app.deploy.bodyAsString.run(content = string)
        assertM(res)(equalTo(string.length.toString))
      }
    } +
      testM("POST Request.getBody") {
        val app = Http.collectZIO[Request] { case req => req.body.as(Response.ok) }
        val res = app.deploy.status.run(path = !!, method = Method.POST, content = "some text")
        assertM(res)(equalTo(Status.OK))
      }
  }

  def responseSpec = suite("ResponseSpec") {
    testM("data") {
      checkAllM(nonEmptyContent) { case (string, data) =>
        val res = Http.fromData(data).deploy.bodyAsString.run()
        assertM(res)(equalTo(string))
      }
    } +
      testM("data from file") {
        val file = new File(getClass.getResource("/TestFile.txt").getPath)
        val res  = Http.fromFile(file).deploy.bodyAsString.run()
        assertM(res)(equalTo("abc\nfoo"))
      } +
      testM("content-type header on file response") {
        val file = new File(getClass.getResource("/TestFile.txt").getPath)
        val res  =
          Http
            .fromFile(file)
            .deploy
            .headerValue(HeaderNames.contentType)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("text/plain"))
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
          .run(content = "abc")
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
          val expected = (0 to size) map (_ => Status.OK)
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

  def serverStartSpec = suite("ServerStartSpec") {
    testM("desired port") {
      val port = 8088
      (Server.port(port) ++ Server.app(Http.empty)).make.use { start =>
        assertM(ZIO.effect(start.port))(equalTo(port))
      }
    } +
      testM("available port") {
        (Server.port(0) ++ Server.app(Http.empty)).make.use { start =>
          assertM(ZIO.effect(start.port))(not(equalTo(0)))
        }
      }
  }

  override def spec =
    suiteM("Server") {
      app.as(List(serverStartSpec, staticAppSpec, dynamicAppSpec, responseSpec, requestSpec, nonZIOSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def staticAppSpec = suite("StaticAppSpec") {
    testM("200 response") {
      val actual = status(path = !! / "success")
      assertM(actual)(equalTo(Status.OK))
    } +
      testM("500 response") {
        val actual = status(path = !! / "failure")
        assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
      } +
      testM("404 response") {
        val actual = status(path = !! / "random")
        assertM(actual)(equalTo(Status.NOT_FOUND))
      } +
      testM("200 response with encoded path") {
        val actual = status(path = !! / "get%2Fsuccess")
        assertM(actual)(equalTo(Status.OK))
      } +
      testM("Multiple 200 response") {
        for {
          data <- status(path = !! / "success").repeatN(1024)
        } yield assertTrue(data == Status.OK)
      }
  }

  def nonZIOSpec = suite("NonZIOSpec") {
    testM("200 response") {
      checkAllM(HttpGen.method) { method =>
        val actual = status(method, !! / "HExitSuccess")
        assertM(actual)(equalTo(Status.OK))
      }
    } +
      testM("500 response") {
        checkAllM(HttpGen.method) { method =>
          val actual = status(method, !! / "HExitFailure")
          assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
        }
      } +
      testM("404 response ") {
        checkAllM(HttpGen.method) { method =>
          val actual = status(method, !! / "A")
          assertM(actual)(equalTo(Status.NOT_FOUND))
        }
      }

  }
}
