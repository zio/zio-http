package zhttp

import zhttp.http.Http
import zio.ZIO

package object test {
  implicit class HttpWithTest[R, E, A, B](http: Http[R, E, A, B]) {
    def apply(req: A): ZIO[R, Option[E], B] = http.execute(req).evaluate.asEffect
  }
}
