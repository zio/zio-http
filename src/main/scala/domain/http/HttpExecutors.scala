package zio-http.domain.http

import zio.ZIO

trait HttpExecutors {
  import Http._
  def execute[R, E, A, B](http: Http[R, E, A, B], a: => A): ZIO[R, E, B] = executePartial(http, a).asEffect

  def executePartial[R, E, A, B](http: Http[R, E, A, B], a: => A): HttpResult[R, E, B] = {
    http match {
      // Stackless
      case Identity              => HttpResult.success(a.asInstanceOf[B])
      case Succeed(b)            => HttpResult.success(b)
      case Fail(e)               => HttpResult.failure(e)
      case FromEffectFunction(f) => HttpResult.continue(f(a))

      // Stackfull
      case Chain(self, other) =>
        executePartial(self, a) >>= { b => executePartial(other, b) }

      case FoldM(http, ee, bb) =>
        executePartial(http, a)
          .foldM(
            e => executePartial(ee(e), a),
            b => executePartial(bb(b), a),
          )

      case Concat(self, other, ev) =>
        executePartial(self, a).catchAll(e =>
          if (ev.asInstanceOf[CanConcatenate[E]].is(e)) executePartial(other, a) else HttpResult.failure(e),
        )
    }
  }
}
