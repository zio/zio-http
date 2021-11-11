package zhttp.service

import zhttp.http._
import zhttp.service.server.Auto
import zio.test.Assertion.{isNone, isPositive, isSome}
import zio.test.assertM
import zio.{Ref, UIO, ZIO, ZRef}

import scala.util.Try

object ClientContentLengthSpec extends HttpRunnableSpec(8083) {

  type ServerState = Map[String, Int]

  val env = EventLoopGroup.auto() ++ ChannelFactory.auto // ++ ServerChannelFactory.auto

  val contentLengthName = "content-length"

  def getContentLength(headers: List[Header]): Option[Int] =
    headers
      .find(_.name.toString.toLowerCase == "content-length")
      .flatMap(d => tryParseInt(d.value.toString))

  def tryParseInt(s: String): Option[Int] = Try(s.toInt).toOption

  def updateState(state: ServerState, headers: List[Header], path: String): ServerState =
    getContentLength(headers) match {
      case Some(length) if !state.contains(path) => state.updated(path, length)
      case _                                     => state
    }

  def getApp(state: Ref[ServerState])(p: Int): Server[Any, Throwable] = {
    val app: HttpApp[Any, Throwable] = HttpApp.collectM { case req @ _ -> !! / path =>
      state.update(updateState(_, req.getHeaders, path)) *> ZIO.succeed(Response.ok)
    }
    Server.port(p) ++    // Setup port
      Server.app(app) ++ // Setup the Http app
      Server.transport(Auto)
  }

  def getLengthForPath(state: Ref[ServerState], path: String): UIO[Option[Int]] = {
    state.get.map(_.get(path))
  }

  def serverAppState(p: Int) =
    for {
      state <- ZRef.make(Map[String, Int]()).toManaged_
      _     <- getApp(state)(p).make
    } yield state

  override def spec = suite("Client Content-Length auto assign")(
    testM("get request without content") {
      val p = 38083
      serverAppState(p).use { state =>
        val path   = "getWithoutContent"
        val actual = statusWithPort(p, !! / path) *> getLengthForPath(state, path)
        assertM(actual)(isNone)
      }
    },
    testM("post request with nonempty content") {
      val p = 38084
      serverAppState(p).use { state =>
        val path    = "postWithNonemptyContent"
        val content = "content"
        val actual  = requestWithPort(p, !! / path, Method.POST, content) *> getLengthForPath(state, path)
        assertM(actual)(isSome(isPositive[Int]))
      }
    },
    testM("post request with nonempty content and set content-length") {
      val p = 38085
      serverAppState(p).use { state =>
        val path    = "postWithNonemptyContentAndSetContentLength"
        val content = "content"
        val headers = List(Header.custom(contentLengthName, "dummy"))
        val actual  = requestWithPort(p, !! / path, Method.POST, content, headers) *> getLengthForPath(state, path)
        assertM(actual)(isSome(isPositive[Int]))
      }
    },
  ).provideCustomLayer(env)

}
