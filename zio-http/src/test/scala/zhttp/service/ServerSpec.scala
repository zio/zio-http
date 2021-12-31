package zhttp.service

import zhttp.html._
import zhttp.http._
import zhttp.internal.{AppCollection, HttpGen, HttpRunnableSpec}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import java.nio.file.Paths

object ServerSpec extends HttpRunnableSpec(8088) {

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyHttpData(Gen.const(data))
  } yield (data.mkString(""), content)
  private val env       = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live
  private val staticApp = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
    case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
    case Method.GET -> !! / "get%2Fsuccess" => ZIO.succeed(Response.ok)
  }
  private val app       = serve { staticApp ++ AppCollection.app }

  def dynamicAppSpec = suite("DynamicAppSpec") {
    suite("success") {
      testM("status is 200") {
        val status = Http.ok.requestStatus()
        assertM(status)(equalTo(Status.OK))
      } +
        testM("status is 200") {
          val res = Http.text("ABC").requestStatus()
          assertM(res)(equalTo(Status.OK))
        } +
        testM("content is set") {
          val res = Http.text("ABC").requestBodyAsString()
          assertM(res)(containsString("ABC"))
        }
    } +
      suite("not found") {
        val app = Http.empty
        testM("status is 404") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.NOT_FOUND))
        } +
          testM("header is set") {
            val res = app.request().map(_.getHeaderValue("Content-Length"))
            assertM(res)(isSome(equalTo("0")))
          }
      } +
      suite("error") {
        val app = Http.fail(new Error("SERVER_ERROR"))
        testM("status is 500") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
        } +
          testM("content is set") {
            val res = app.requestBodyAsString()
            assertM(res)(containsString("SERVER_ERROR"))
          } +
          testM("header is set") {
            val res = app.request().map(_.getHeaderValue("Content-Length"))
            assertM(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val app = Http.collectZIO[Request] { case req =>
          req.getBodyAsString.map(text => Response.text(text))
        }

        testM("status is 200") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.OK))
        } +
          testM("body is ok") {
            val res = app.requestBodyAsString(content = "ABC")
            assertM(res)(equalTo("ABC"))
          } +
          testM("empty string") {
            val res = app.requestBodyAsString(content = "")
            assertM(res)(equalTo(""))
          } +
          testM("one char") {
            val res = app.requestBodyAsString(content = "1")
            assertM(res)(equalTo("1"))
          }
      } +
      suite("headers") {
        val app = Http.ok.addHeader("Foo", "Bar")
        testM("headers are set") {
          val res = app.request().map(_.getHeaderValue("Foo"))
          assertM(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = Http.response(Response(status = Status.OK, data = HttpData.fromString("abc")))
        testM("body is set") {
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
    testM("data") {
      checkAllM(nonEmptyContent) { case (string, data) =>
        val res = Http.fromData(data).requestBodyAsString()
        assertM(res)(equalTo(string))
      }
    } +
      testM("status") {
        checkAllM(HttpGen.status) { case (status) =>
          val res = Http.status(status).requestStatus()
          assertM(res)(equalTo(status))
        }
      } +
      testM("header") {
        checkAllM(HttpGen.header) { case header @ (name, value) =>
          val res = Http.ok.addHeader(header).requestHeaderValueByName()(name)
          assertM(res)(isSome(equalTo(value)))
        }
      } +
      testM("file-streaming") {
        val path = getClass.getResource("/TestFile").getPath
        val res  = Http
          .fromData(HttpData.fromStream(ZStream.fromFile(Paths.get(path))))
          .requestBodyAsString()
        assertM(res)(containsString("foo"))
      } +
      suite("html") {
        testM("body") {
          val res = Http.html(html(body(div(id := "foo", "bar")))).requestBodyAsString()
          assertM(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
        } +
          testM("content-type") {
            val app = Http.html(html(body(div(id := "foo", "bar"))))
            val res = app.requestHeaderValueByName()(HeaderNames.contentType)
            assertM(res)(isSome(equalTo(HeaderValues.textHtml.toString)))
          }
      } +
      suite("content-length") {
        suite("string") {
          testM("unicode text") {
            val res = Http.text("äöü").requestContentLength()
            assertM(res)(isSome(equalTo(6L)))
          } +
            testM("already set") {
              val res = Http.text("1234567890").withContentLength(4L).requestContentLength()
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
            actual   <- ZIO.foreachPar(0 to size)(_ => Http.response(response).requestStatus())
          } yield assert(actual)(equalTo(expected))
        } +
          testM("update after cache") {
            val server = "ZIO-Http"
            for {
              res    <- Response.text("abc").freeze
              actual <- Http.response(res).withServer(server).requestHeaderValueByName()(HeaderNames.server)
            } yield assert(actual)(isSome(equalTo(server)))
          }
      }
  }

  override def spec = {
    suiteM("Server") {
      app.as(List(staticAppSpec, dynamicAppSpec, responseSpec, requestSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(30 seconds)
  }

  def staticAppSpec = suite("StaticAppSpec") {
    testM("200 response") {
      val actual = status(!! / "success")
      assertM(actual)(equalTo(Status.OK))
    } +
      testM("500 response") {
        val actual = status(!! / "failure")
        assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
      } +
      testM("404 response") {
        val actual = status(!! / "random")
        assertM(actual)(equalTo(Status.NOT_FOUND))
      } +
      testM("200 response with encoded path") {
        val actual = status(!! / "get%2Fsuccess")
        assertM(actual)(equalTo(Status.OK))
      } +
      testM("Multiple 200 response") {
        for {
          data <- status(!! / "success").repeatN(1024)
        } yield assertTrue(data == Status.OK)
      }
  }
}
