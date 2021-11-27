package zhttp.service

import zhttp.http._
import zhttp.internal.{AppCollection, HttpGen}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.{anything, containsString, equalTo, isSome}
import zio.test.TestAspect._
import zio.test.{Gen, assertM, checkAllM}

object ServerSpec extends HttpRunnableSpec(8088) {

  def dynamicAppSpec = suite("DynamicAppSpec") {
    suite("success") {
      testM("status is 200") {
        val status = HttpApp.ok.requestStatus()
        assertM(status)(equalTo(Status.OK))
      } +
        testM("status is 200") {
          val res = HttpApp.text("ABC").requestStatus()
          assertM(res)(equalTo(Status.OK))
        } +
        testM("content is set") {
          val res = HttpApp.text("ABC").requestBodyAsString()
          assertM(res)(containsString("ABC"))
        }
    } +
      suite("not found") {
        val app = HttpApp.empty
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
        val app = HttpApp.fail(new Error("SERVER_ERROR"))
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
        val app = HttpApp.collectM { case req =>
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
        val app = HttpApp.ok.addHeader("Foo", "Bar")
        testM("headers are set") {
          val res = app.request().map(_.getHeaderValue("Foo"))
          assertM(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = HttpApp.response(Response(status = Status.OK, data = HttpData.fromText("abc")))
        testM("body is set") {
          val res = app.requestBodyAsString()
          assertM(res)(equalTo("abc"))
        }
      }
  }

  def responseSpec = suite("ResponseSpec") {
    testM("data") {
      checkAllM(nonEmptyContent) { case (string, data) =>
        val res = HttpApp.data(data).requestBodyAsString()
        assertM(res)(equalTo(string))
      }
    } +
      testM("status") {
        checkAllM(HttpGen.status) { case (status) =>
          val res = HttpApp.status(status).requestStatus()
          assertM(res)(equalTo(status))
        }
      } +
      testM("header") {
        checkAllM(HttpGen.header) { case (header) =>
          val res = HttpApp.ok.addHeader(header).requestHeaderValueByName()(header.name)
          assertM(res)(isSome(equalTo(header.value)))
        }
      }
  }

  override def spec = {
    suiteM("Server") {
      app.as(List(staticAppSpec, dynamicAppSpec, responseSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(Int.MaxValue seconds)
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
      }
  }

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyHttpData(Gen.const(data))
  } yield (data.mkString(""), content)

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live

  private val staticApp = HttpApp.collectM {
    case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
    case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
    case Method.GET -> !! / "get%2Fsuccess" => ZIO.succeed(Response.ok)
  }

  private val app = serve { staticApp +++ AppCollection.app }
}
