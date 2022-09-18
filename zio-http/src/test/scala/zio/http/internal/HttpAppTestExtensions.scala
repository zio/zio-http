package zio.http.internal

import zio.http._
import zio.http.model._

trait HttpAppTestExtensions {
  implicit class HttpAppSyntax[R, E](app: HttpApp[R, E]) {
    def header(name: String): Http[R, E, Request, Option[String]] =
      app.map(res => res.headerValue(name))

    def headerValues: Http[R, E, Request, List[String]] =
      app.map(res => res.headers.toList.map(_._2.toString))

    def headers: Http[R, E, Request, Headers] =
      app.map(res => res.headers)

    def status: Http[R, E, Request, Status] =
      app.map(res => res.status)
  }
}
