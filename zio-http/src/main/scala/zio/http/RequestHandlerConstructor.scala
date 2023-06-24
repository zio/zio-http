package zio.http

import zio.ZIO

trait RequestHandlerConstructor[H] {
  type Env
  type Err

  def toHandler(h: H): Handler[Env, Err, Request, Response]
}
object RequestHandlerConstructor extends RequestHandlerConstructorLowPriorityImplicits0 {

  implicit def handlerIsRequestHandlerConstructor[Env0, Err0]
    : RequestHandlerConstructor.Typed[Handler[Env0, Err0, Request, Response], Env0, Err0] =
    new RequestHandlerConstructor[Handler[Env0, Err0, Request, Response]] {
      type Env = Env0
      type Err = Err0
      type Z   = Handler[Env, Err, Request, Response]

      def toHandler(z: Z): Handler[Env, Err, Request, Response] =
        z
    }
}

private[http] trait RequestHandlerConstructorLowPriorityImplicits0
    extends RequestHandlerConstructorLowPriorityImplicits1 {
  implicit def functionZIOIsRequestHandlerConstructor[Env0, Err0]
    : RequestHandlerConstructor.Typed[Request => ZIO[Env0, Err0, Response], Env0, Err0] =
    new RequestHandlerConstructor[Request => ZIO[Env0, Err0, Response]] {
      type Env = Env0
      type Err = Err0
      type Z   = Request => ZIO[Env0, Err0, Response]

      def toHandler(z: Z): Handler[Env, Err, Request, Response] =
        Handler.fromFunctionZIO(z)
    }

}

private[http] trait RequestHandlerConstructorLowPriorityImplicits1
    extends RequestHandlerConstructorLowPriorityImplicits2 {
  implicit val functionIsRequestHandlerConstructor: RequestHandlerConstructor.Typed[Request => Response, Any, Nothing] =
    new RequestHandlerConstructor[Request => Response] {
      type Env = Any
      type Err = Nothing
      type Z   = Request => Response

      def toHandler(z: Z): Handler[Env, Err, Request, Response] =
        Handler.fromFunction(z)
    }

}

private[http] trait RequestHandlerConstructorLowPriorityImplicits2 {
  type Typed[H, Env0, Err0] = RequestHandlerConstructor[H] { type Env = Env0; type Err = Err0 }

  implicit val responseIsRequestHandlerConstructor: RequestHandlerConstructor.Typed[Response, Any, Nothing] =
    new RequestHandlerConstructor[Response] {
      type Env = Any
      type Err = Nothing
      type Z   = Response

      def toHandler(z: Z): Handler[Env, Err, Request, Response] =
        Handler.succeed(z)
    }

  implicit val bodyIsRequestHandlerConstructor: RequestHandlerConstructor.Typed[Body, Any, Nothing] =
    new RequestHandlerConstructor[Body] {
      type Env = Any
      type Err = Nothing
      type Z   = Body

      def toHandler(z: Z): Handler[Env, Err, Request, Response] =
        Handler.succeed(Response(body = z))
    }
}
