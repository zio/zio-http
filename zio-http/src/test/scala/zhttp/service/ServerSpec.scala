package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.html._
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zhttp.service.server._
import zio._
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

  private val app = serve { staticApp ++ DynamicServer.app }

  def dynamicAppSpec = suite("DynamicAppSpec") {
    suite("success") {
      test("status is 200") {
        val status = Http.ok.requestStatus()
        assertM(status)(equalTo(Status.OK))
      } +
        test("status is 200") {
          val res = Http.text("ABC").requestStatus()
          assertM(res)(equalTo(Status.OK))
        } +
        test("content is set") {
          val res = Http.text("ABC").requestBodyAsString()
          assertM(res)(containsString("ABC"))
        }
    } +
      suite("not found") {
        val app = Http.empty
        test("status is 404") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.NOT_FOUND))
        } +
          test("header is set") {
            val res = app.request().map(_.getHeaderValue("Content-Length"))
            assertM(res)(isSome(equalTo("0")))
          }
      } +
      suite("error") {
        val app = Http.fail(new Error("SERVER_ERROR"))
        test("status is 500") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
        } +
          test("content is set") {
            val res = app.requestBodyAsString()
            assertM(res)(containsString("SERVER_ERROR"))
          } +
          test("header is set") {
            val res = app.request().map(_.getHeaderValue("Content-Length"))
            assertM(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val app = Http.collectZIO[Request] { case req =>
          req.getBodyAsString.map(text => Response.text(text))
        }

        test("status is 200") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.OK))
        } +
          test("body is ok") {
            val res = app.requestBodyAsString(content = "ABC")
            assertM(res)(equalTo("ABC"))
          } +
          test("empty string") {
            val res = app.requestBodyAsString(content = "")
            assertM(res)(equalTo(""))
          } +
          test("one char") {
            val res = app.requestBodyAsString(content = "1")
            assertM(res)(equalTo("1"))
          }
      } +
      suite("headers") {
        val app = Http.ok.addHeader("Foo", "Bar")
        test("headers are set") {
          val res = app.request().map(_.getHeaderValue("Foo"))
          assertM(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = Http.response(Response(status = Status.OK, data = HttpData.fromString("abc")))
        test("body is set") {
          val res = app.requestBodyAsString()
          assertM(res)(equalTo("abc"))
        }
      }
  }

  def requestSpec = suite("RequestSpec") {
    val app: HttpApp[Any, Nothing] = Http.collect[Request] { case req =>
      Response.text(req.getContentLength.getOrElse(-1).toString)
    }
    testM("has content-length") {
      checkAllM(Gen.alphaNumericString) { string =>
        val res = app.requestBodyAsString(content = string)
        assertM(res)(equalTo(string.length.toString))
      }
    } +
      testM("POST Request.getBody") {
        val app = Http.collectZIO[Request] { case req => req.getBody.as(Response.ok) }
        val res = app.requestStatus(!!, Method.POST, "some text")
        assertM(res)(equalTo(Status.OK))
      }
  }

  def responseSpec = suite("ResponseSpec") {
    test("data") {
      checkAllM(nonEmptyContent) { case (string, data) =>
        val res = Http.fromData(data).requestBodyAsString()
        assertM(res)(equalTo(string))
      }
    } +
      test("data from file") {
        val file = new File(getClass.getResource("/TestFile.txt").getPath)
        val res  = Http.fromFile(file).requestBodyAsString()
        assertM(res)(equalTo("abc\nfoo"))
      } +
      test("content-type header on file response") {
        val file = new File(getClass.getResource("/TestFile.txt").getPath)
        val res  =
          Http
            .fromFile(file)
            .requestHeaderValueByName()(HttpHeaderNames.CONTENT_TYPE)
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("text/plain"))
      } +
      test("status") {
        checkAll(HttpGen.status) { case (status) =>
          val res = Http.status(status).requestStatus()
          assertM(res)(equalTo(status))
        }
      } +
      test("header") {
        checkAll(HttpGen.header) { case header @ (name, value) =>
          val res = Http.ok.addHeader(header).requestHeaderValueByName()(name)
          assertM(res)(isSome(equalTo(value)))
        }
      } +
      test("text streaming") {
        val res = Http.fromStream(ZStream("a", "b", "c")).requestBodyAsString()
        assertM(res)(equalTo("abc"))
      } +
      test("file-streaming") {
        val path = getClass.getResource("/TestFile.txt").getPath
        val res  = Http.fromStream(ZStream.fromFile(Paths.get(path))).requestBodyAsString()
        assertM(res)(equalTo("abc\nfoo"))
      } +
      suite("html") {
        test("body") {
          val res = Http.html(html(body(div(id := "foo", "bar")))).requestBodyAsString()
          assertM(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
        } +
          test("content-type") {
            val app = Http.html(html(body(div(id := "foo", "bar"))))
            val res = app.requestHeaderValueByName()(HeaderNames.contentType)
            assertM(res)(isSome(equalTo(HeaderValues.textHtml.toString)))
          }
      } +
      suite("content-length") {
        suite("string") {
          test("unicode text") {
            val res = Http.text("äöü").requestContentLength()
            assertM(res)(isSome(equalTo(6L)))
          } +
            test("already set") {
              val res = Http.text("1234567890").withContentLength(4L).requestContentLength()
              assertM(res)(isSome(equalTo(4L)))
            }
        }
      } +
      suite("memoize") {
        test("concurrent") {
          val size     = 100
          val expected = (0 to size) map (_ => Status.OK)
          for {
            response <- Response.text("abc").freeze
            actual   <- ZIO.foreachPar(0 to size)(_ => Http.response(response).requestStatus())
          } yield assert(actual)(equalTo(expected))
        } +
          test("update after cache") {
            val server = "ZIO-Http"
            for {
              res    <- Response.text("abc").freeze
              actual <- Http.response(res).withServer(server).requestHeaderValueByName()(HeaderNames.server)
            } yield assert(actual)(isSome(equalTo(server)))
          }
      }
  }

  override def spec =
    suite("Server") {
      app.as(List(serverStartSpec, staticAppSpec, dynamicAppSpec, responseSpec, requestSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def serverStartSpec = suite("ServerStartSpec") {
    test("desired port") {
      val port = 8088
      (Server.port(port) ++ Server.app(Http.empty)).make.use { start =>
        assertM(ZIO.effect(start.port))(equalTo(port))
      }
    } +
      test("available port") {
        (Server.port(0) ++ Server.app(Http.empty)).make.use { start =>
          assertM(ZIO.effect(start.port))(not(equalTo(0)))
        }
      }
  }

  def staticAppSpec = suite("StaticAppSpec") {
    test("200 response") {
      val actual = status(!! / "success")
      assertM(actual)(equalTo(Status.OK))
    } +
      test("500 response") {
        val actual = status(!! / "failure")
        assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
      } +
      test("404 response") {
        val actual = status(!! / "random")
        assertM(actual)(equalTo(Status.NOT_FOUND))
      } +
      test("200 response with encoded path") {
        val actual = status(!! / "get%2Fsuccess")
        assertM(actual)(equalTo(Status.OK))
      } +
      test("Multiple 200 response") {
        for {
          data <- status(!! / "success").repeatN(1024)
        } yield assertTrue(data == Status.OK)
      }
  }
}
