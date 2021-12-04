package zhttp.http

import io.netty.handler.codec.http.HttpVersion
import zhttp.internal.HttpGen
import zhttp.service.EncodeClientParams
import zio.test.Assertion._
import zio.test._

object EncodeClientParamsSpec extends DefaultRunnableSpec with EncodeClientParams {

  def spec = suite("EncodeClientParams")(
    testM("encodeClientParams") {
      check(HttpGen.clientParams) { case params =>
        val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
        assert(req.method())(equalTo(params.method.asHttpMethod)) &&
        assert(req.uri())(equalTo(params.url.asString))
      }
    },
  )
}
