//package zhttp.service
//
//import zhttp.http._
//import zio.test.Assertion.{isNone, isPositive, isSome}
//import zio.test.assertM
//import zio.{Ref, UIO, ZIO, ZManaged, ZRef}
//
//import scala.util.Try
//
//object ClientContentLengthSpec extends HttpRunnableSpec(8083) {
//
//  type ServerState = Map[String, Int]
//
//  val env = EventLoopGroup.auto() ++ ChannelFactory.auto
//
//  val contentLengthName = "content-length"
//
//  def getContentLength(headers: List[Header]): Option[Int] =
//    headers
//      .find(_.name.toString.toLowerCase == "content-length")
//      .flatMap(d => tryParseInt(d.value.toString))
//
//  def tryParseInt(s: String): Option[Int] = Try(s.toInt).toOption
//
//  def updateState(state: ServerState, headers: List[Header], path: String): ServerState =
//    getContentLength(headers) match {
//      case Some(length) if !state.contains(path) => state.updated(path, length)
//      case _ => state
//    }
//
////  def getApp(state: Ref[ServerState]) = serve {
////    HttpApp.collectM { case req@_ -> !! / path =>
////      state.update(updateState(_, req.getHeaders, path)) *> ZIO.succeed(Response.ok)
////    }
////  }
//
//  def getApp(state: ZManaged[Any,Nothing,Ref[Map[String,Int]]]) =  {
//    for {
//      st <- state
//      httpApp <- serve{
//        HttpApp.collectM { case req@_ -> !! / path =>
//          st.update(updateState(_, req.getHeaders, path)) *> ZIO.succeed(Response.ok)
//        }
//      }
//    } yield {
//      httpApp
//    }
//  }
//
//  def getLengthForPath(state: Ref[ServerState], path: String): UIO[Option[Int]] = {
//    state.get.map(_.get(path))
//  }
//
////  val serverAppState =
////    for {
////      state <- ZRef.make(Map[String, Int]()).toManaged_
////      _ <- getApp(state)
////    } yield state
//
//  override def spec = {
//    val state: ZManaged[Any,Nothing,Ref[Map[String,Int]]] =
//      ZRef.make(Map[String, Int]()).toManaged_
//
//    suite("Client Content-Length auto assign") {
//      for {
//        st <- state
//      } yield {
//        testM("get request without content") {
//          val path = "getWithoutContent"
//          val actual = status(!! / path) *> getLengthForPath(st, path)
//          assertM(actual)(isNone)
//        } +
//          testM("post request with nonempty content") {
//            val path = "postWithNonemptyContent"
//            val content = "content"
//            val actual = request(!! / path, Method.POST, content) *> getLengthForPath(st, path)
//            assertM(actual)(isSome(isPositive[Int]))
//          } +
//          testM("post request with nonempty content and set content-length") {
//            val path = "postWithNonemptyContentAndSetContentLength"
//            val content = "content"
//            val headers = List(Header.custom(contentLengthName, "dummy"))
//            val actual = request(!! / path, Method.POST, content, headers) *> getLengthForPath(st, path)
//            assertM(actual)(isSome(isPositive[Int]))
//          }
//      }
//    }.provideCustomLayerShared(env ++ (getApp(state).toLayer.orDie))
//  }
//
//}