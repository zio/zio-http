package zhttp.service

import zhttp.http._
import zio.test.Assertion.{isNone, isPositive, isSome}
import zio.test.{TestFailure, assertM}
import zio.{Has, Ref, UIO, ZIO, ZRef}

import scala.util.Try

object ClientContentLengthSpec extends HttpRunnableSpec(8083) {

  type ServerState    = Map[String, Int]
  type ServerStateRef = Ref[ServerState]
  type HasServerState = Has[ServerStateRef]

  val env = EventLoopGroup.auto() ++ ChannelFactory.auto

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
    HttpApp.collectM { case req @ _ -> !! / path =>
      state.update(updateState(_, req.getHeaders, path)) *> ZIO.succeed(Response.ok)
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

  /*
    Issue encountered with scala 3 Has[Ref] requires initialization of layer
    before use within provide.
    https://github.com/zio/zio/issues/4802
   */
  val serverAppStateLayer = serverAppState.toLayer

  override def spec =
    suite("Client Content-Length auto assign") {
      testM("get request without content") {
        val path = "getWithoutContent"
        val eff: ZIO[EventLoopGroup with ChannelFactory with HasServerState, Throwable, Option[Int]] =
          for {
            state  <- ZIO.access[HasServerState](_.get)
            actual <- status(!! / path) *> getLengthForPath(state, path)
          } yield actual
        assertM(eff)(isNone)
      } +
        testM("post request with nonempty content") {
          val path    = "postWithNonemptyContent"
          val content = "content"
          val eff: ZIO[EventLoopGroup with ChannelFactory with HasServerState, Throwable, Option[Int]] =
            for {
              state  <- ZIO.access[HasServerState](_.get)
              actual <- request(!! / path, Method.POST, content) *> getLengthForPath(state, path)
            } yield actual
          assertM(eff)(isSome(isPositive[Int]))
        } +
        testM("post request with nonempty content and set content-length") {
          val path    = "postWithNonemptyContentAndSetContentLength"
          val content = "content"
          val headers = List(Header.custom(contentLengthName, "dummy"))
          val eff: ZIO[EventLoopGroup with ChannelFactory with HasServerState, Throwable, Option[Int]] =
            for {
              state  <- ZIO.access[HasServerState](_.get)
              actual <- request(!! / path, Method.POST, content, headers) *> getLengthForPath(state, path)
            } yield actual
          assertM(eff)(isSome(isPositive[Int]))
        }
    }.provideCustomLayerShared(env ++ serverAppStateLayer)
      .mapError(TestFailure.fail)

}
