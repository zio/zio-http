package zhttp

import zhttp.http.{Http, HttpResult}

/**
 * Helper methods for executing Requests against a non-bound Http server.
 */
object TestHttp {
  def testExecute[R, E, A, B](http: Http[R, E, A, B], req: A): HttpResult[R, E, B] = http.execute(req)
}

