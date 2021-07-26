package zhttp

import zhttp.http.{Http, HttpResult}

package object test {
  implicit class HttpWithTest[R, E, A, B](http: Http[R, E, A, B]) {
    def apply(req: A): HttpResult[R, E, B] = http.execute(req)
  }
}

