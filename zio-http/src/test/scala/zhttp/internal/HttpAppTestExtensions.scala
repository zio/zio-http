package zhttp.internal

import zhttp.http.{Http, HttpApp, Request}

trait HttpAppTestExtensions {
  implicit class HttpAppSyntax[R, E](app: HttpApp[R, E]) {
    def getHeader(name: String): Http[R, E, Request, Option[String]] =
      app.asHttp.map(res => res.getHeaderValue(name))
  }
}
