package zio.http.api

import io.netty.handler.codec.http.HttpHeaderNames
import zio.ZIO
import zio.http.middleware.Auth
import zio.http.model.{HeaderNames, Headers}

final case class MiddlewareSpec[MiddlewareIn, MiddlewareOut](
  middlewareIn: In[In.HeaderType with In.QueryType, MiddlewareIn],
  middlewareOut: In[In.HeaderType with In.QueryType, MiddlewareOut],
) { self =>
  def toMiddleware[R, E](f: MiddlewareIn => ZIO[R, E, MiddlewareOut]): Middleware[R, E, MiddlewareIn, MiddlewareOut] =
    Middleware.HandlerZIO(self, f)

  def ++[MiddlewareIn2, MiddlewareOut2](that: MiddlewareSpec[MiddlewareIn2, MiddlewareOut2])(implicit
    inCombiner: Combiner[MiddlewareIn, MiddlewareIn2],
    outCombiner: Combiner[MiddlewareOut, MiddlewareOut2],
  ): MiddlewareSpec[inCombiner.Out, outCombiner.Out] =
    MiddlewareSpec(self.middlewareIn ++ that.middlewareIn, self.middlewareOut ++ that.middlewareOut)

  def mapIn[MiddlewareIn2](f: In[MiddlewareIn] => In[MiddlewareIn2]): MiddlewareSpec[MiddlewareIn2, MiddlewareOut] =
    copy(middlewareIn = f(middlewareIn))

  def mapOut[MiddlewareOut2](f: In[MiddlewareOut] => In[MiddlewareOut2]): MiddlewareSpec[MiddlewareIn, MiddlewareOut2] =
    copy(middlewareOut = f(middlewareOut))

  def mapBoth[MiddlewareIn2, MiddlewareOut2](
    f: In[MiddlewareIn] => In[MiddlewareIn2],
    g: In[MiddlewareOut] => In[MiddlewareOut2],
  ): MiddlewareSpec[MiddlewareIn2, MiddlewareOut2] =
    mapIn(f).mapOut(g)

}

object MiddlewareSpec {

  def empty: MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(In.empty, In.empty)

  /**
   * Add specified header to the response
   */
  def addHeader(key: String, value: String): MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(In.empty, In.header(key, TextCodec.constant(value)))

  // FIXME
  // Parse string to Credentials
  val auth: MiddlewareSpec[Auth.Credentials, Unit] =
    requireHeader(HeaderNames.wwwAuthenticate.toString)
      .mapIn(_.transform(s => Auth.Credentials(???, ???), c => s"${c.uname}:${c.upassword}"))

  def requireHeader(name: String): MiddlewareSpec[String, Unit] =
    MiddlewareSpec(In.header(name, TextCodec.string), In.empty)

//  private[api] def toMiddleware(in: In[Unit]) =
//    in match {
//      case atom: In.Atom[_] =>
//        atom match {
//          case In.BasicAuthenticate(a, b) => Middleware.basicAuth(a, b)
//          case In.Header(name, value)     =>
//            value match {
//              case TextCodec.Constant(value) => Middleware.addHeader(name, value)
//              case _                         => Middleware.empty
//            }
//          case _                          => Middleware.empty
//        }
//      case _                => Middleware.empty
//    }
}
