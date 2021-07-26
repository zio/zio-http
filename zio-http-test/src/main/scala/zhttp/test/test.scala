package zhttp

import zhttp.http.{Http, HttpResult}
import zio.ZIO

package object test {
  implicit class HttpWithTest[R, E, A, B](http: Http[R, E, A, B]) {
    def apply(req: A): zio.UIO[HttpResult[R, E, B]] = ZIO.succeed(http.execute(req))
  }
}

