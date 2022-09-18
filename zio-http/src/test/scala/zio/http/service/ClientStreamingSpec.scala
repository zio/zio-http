package zio.http.service

import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model.Method
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test.{Spec, TestEnvironment, assertZIO}
import zio.{Scope, durationInt}

object ClientStreamingSpec extends HttpRunnableSpec {

  def clientStreamingSpec = suite("ClientStreamingSpec")(
    test("streaming content from server - extended") {
      val app    = Http.collect[Request] { case req => Response(body = Body.fromStream(req.body.asStream)) }
      val stream = ZStream.fromIterable(List("This ", "is ", "a ", "longer ", "text."))
      val res    = app.deployChunked.body
        .run(method = Method.POST, body = Body.fromStream(stream))
        .flatMap(_.asString)
      assertZIO(res)(equalTo("This is a longer text."))
    },
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ClientProxy") {
    serve(DynamicServer.app).as(List(clientStreamingSpec))
  }.provideShared(
    Scope.default,
    DynamicServer.live,
    severTestLayer,
    Client.live,
    ClientConfig.live(ClientConfig.empty.useObjectAggregator(false)),
  ) @@
    timeout(5 seconds) @@ sequential
}
