package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.internal.HttpRunnableSpec.HttpIO
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server.ServerChannelFactory
import zio.duration.durationInt
import zio.test.Assertion.{containsString, equalTo, isSome}
import zio.test.TestAspect.timeout
import zio.test.{ZSpec, assertM}

import java.nio.file.Paths

object StaticFileServerSpec extends HttpRunnableSpec {

  private val staticApp  = Http.serveFilesFrom(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
  private val staticApp2 = Http.serveFilesFrom(Paths.get(getClass.getResource("/TestStatic").getPath))

  val app         = serve { staticApp }
  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  def staticSpec = suite("Static File Server")(
    testM("should list contents of root directory") {
      val res: HttpIO[Any, String] = staticApp.requestBodyAsString()
      assertM(res)(containsString("<li><a href=\"TestFile2.txt\">TestFile2.txt</a></li>"))
    },
    testM("should show file contents") {
      val res = staticApp.requestBodyAsString(path = Path("/TestFile2.txt"))
      assertM(res)(equalTo("This is a test file for testing Static File Server."))
    },
    testM("should have content-type header \"application/json\"") {
      val res = staticApp.request(path = Path("/TestFile3.json")).map(_.getHeaderValue(HttpHeaderNames.CONTENT_TYPE))
      assertM(res)(isSome(equalTo("application/json")))
    },
    testM("should respond with \"Not Found\"") {
      val res = staticApp.requestBodyAsString(path = Path("/NonExistentFile.txt"))
      assertM(res)(equalTo("The requested URI \"/NonExistentFile.txt\" was not found on this server\n"))
    },
    testM("should respond with contents of TestFile2.txt") {
      val res = staticApp2.requestBodyAsString(path = Path("/Folder2/TestFile2.txt"))
      assertM(res)(equalTo("This is a test file for testing Static File Server."))
    },
  )

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suiteM("StaticFileServer") {
    app.as(List(staticSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)
}
