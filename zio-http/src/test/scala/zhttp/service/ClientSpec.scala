package zhttp.service

//import zio._
//import zhttp.http._
import zio.test.Assertion.anything
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("https://api.github.com/users/zio/repos")
      assertM(actual)(anything)
    },
//    testM("supports SSL") {
//      val actual =
//        for {
//          url <- ZIO.fromEither(URL.fromString("https://client.badssl.com/"))
//          context <- Ssl.clientContext
//          client <- Client.ssl(context)
//          response <- client.request(Request(Method.GET -> url))
//        } yield response
//      assertM(actual)(anything)
//    },
  ).provideCustomLayer(env)
}
