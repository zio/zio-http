package zhttp.internal

import zhttp.http.Response.HttpResponse
import zhttp.http._
import zio.ZIO

trait IntegrationTestExtensions {
  val addr = "localhost"
  val port = 8090

  implicit class URLSyntax(urlString: String) {
    def url: URL = URL(Path(urlString), URL.Location.Absolute(Scheme.HTTP, addr, port))
  }

  implicit class ResponseSyntax[R, E](zResponse: ZIO[R, E, HttpResponse[R, E]]) {
    def getStatus: ZIO[R, E, Status] = zResponse.map(_.status)

    def getHeaderNames: ZIO[R, E, List[CharSequence]] = zResponse
      .map(response => response.headers.map(_.name.toString))

    def getContent: ZIO[R, E, HttpData[R, E]] = zResponse.map(_.content)
  }
}
