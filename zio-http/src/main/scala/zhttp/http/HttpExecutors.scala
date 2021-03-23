package zhttp.http

trait HttpExecutors {
  import HttpChannel._

  def evalSuspended[R, E, A, B](http: HttpChannel[R, E, A, B], a: => A): HttpResult[R, E, B] =
    http match {
      case Identity              => HttpResult.success(a.asInstanceOf[B])
      case Succeed(b)            => HttpResult.success(b)
      case Fail(e)               => HttpResult.failure(e)
      case FromEffectFunction(f) => HttpResult.continue(f(a))
      case Chain(self, other)    => HttpResult.suspend(self.evalSuspended(a) >>= (other.evalSuspended(_)))
      case FoldM(http, ee, bb)   =>
        HttpResult.suspend(http.evalSuspended(a).foldM(ee(_).evalSuspended(a), bb(_).evalSuspended(a)))
    }

}
