package zio.http.datastar

import java.nio.charset.StandardCharsets

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

trait InvocationExtensions {
  implicit class InvocationOps[P, I, E, O, A <: AuthType](val invocation: Invocation[P, I, E, O, A]) {

    def datastarRequest: String = {
      val request = invocation.endpoint.input.encodeRequest(invocation.input)
      val url     = request.url.encode
      val method  = request.method

      val bodyOption = request.body match {
        case b: Body.StringBody => Some(b.data)
        case b: Body.ArrayBody  => Some(new String(b.data, StandardCharsets.UTF_8))
        case b: Body.ChunkBody  => Some(new String(b.data.toArray, StandardCharsets.UTF_8))
        case _                  => None
      }

      val methodStr = method.toString.toLowerCase

      bodyOption match {
        case Some(body) if method != Method.GET =>
          val escapedBody = body.replace("'", "\\'")
          val contentType = request.headers.get(Header.ContentType).map(_.mediaType.fullType)

          val options = new StringBuilder
          options.append("{")
          options.append(s"body: '$escapedBody'")
          contentType.foreach { ct =>
            options.append(s", contentType: '$ct'")
          }
          options.append("}")

          s"$$$$$methodStr('$url', $options)"

        case _ =>
          s"$$$$$methodStr('$url')"
      }
    }
  }
}
