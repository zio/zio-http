package zio.http.internal

import zio.http._
import zio.http.model._

trait HttpAppTestExtensions {
  implicit class HttpAppSyntax[R, E](route: HttpApp[R, E]) {
    def header(name: String): Http[R, E, Request, Option[String]] =
      route.map(res => res.headerValue(name))

    def headerValues: Http[R, E, Request, List[String]] =
      route.map(res => res.headers.toList.map(_._2.toString))

    def headers: Http[R, E, Request, Headers] =
      route.map(res => res.headers)

    def status: Http[R, E, Request, Status] =
      route.map(res => res.status)
  }

  implicit class RequestHandlerSyntax[R, E](handler: RequestHandler[R, E]) {
    def header(name: String): Handler[R, E, Request, Option[String]] =
      handler.map(res => res.headerValue(name))

    def headerValues: Handler[R, E, Request, List[String]] =
      handler.map(res => res.headers.toList.map(_._2.toString))

    def headers: Handler[R, E, Request, Headers] =
      handler.map(res => res.headers)

    def status: Handler[R, E, Request, Status] =
      handler.map(res => res.status)
  }
}
