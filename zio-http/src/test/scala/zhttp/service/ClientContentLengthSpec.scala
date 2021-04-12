package zhttp.service

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zio.test.Assertion.{isNone, isPositive, isSome}
import zio.test.TestAspect.ignore
import zio.test.assertM
import zio.{Ref, UIO, ZIO, ZRef}

import scala.util.Try

object ClientContentLengthSpec extends HttpRunnableSpec(8083) {

  type ServerState = Map[String, Int]

  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

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

  def getApp(state: Ref[ServerState]) = serve {
    Http.collectM { case Request((_, URL(Root / path, _, _)), headers, _) =>
      state.update(updateState(_, headers, path)) *> ZIO.succeed(Response.ok)
    }
  }

  def getLengthForPath(state: Ref[ServerState], path: String): UIO[Option[Int]] = {
    state.get.map(_.get(path))
  }

  val serverAppState =
    for {
      state <- ZRef.make(Map[String, Int]()).toManaged_
      _     <- getApp(state)
    } yield state

  override def spec = suiteM("Client Content-Length auto assign")(
    serverAppState
      .map((state: Ref[ServerState]) =>
        List(
          testM("get request without content") {
            val path   = "getWithoutContent"
            val actual = status(Root / path) *> getLengthForPath(state, path)
            assertM(actual)(isNone)
          } @@ ignore,
          testM("post request with nonempty content") {
            val path    = "postWithNonemptyContent"
            val content = "content"
            val actual  = request(Root / path, Method.POST, content) *> getLengthForPath(state, path)
            assertM(actual)(isSome(isPositive[Int]))
          },
          testM("post request with nonempty content and set content-length") {
            val path    = "postWithNonemptyContentAndSetContentLength"
            val content = "content"
            val headers = List(Header.custom(contentLengthName, "dummy"))
            val actual  = request(Root / path, Method.POST, content, headers) *> getLengthForPath(state, path)
            assertM(actual)(isSome(isPositive[Int]))
          },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}
