package zio.http

import scala.annotation.implicitNotFound

import zio.ZIO

@implicitNotFound("""
The type ${H} does not appear to be one that can be used to construct a handler. The following types may be used to construct handlers:

 - Handlers:          Handler[Env, Err, In, Out]
 - Responses:         Response
 - ZIO Values:        ZIO[Env, Err, Out]
 - Simple Functions:  In => Out
 - ZIO Functions:     In => ZIO[Env, Err, Out]

If you are having trouble using this function, you can always make a handler directly with the constructors on the [[zio.http.Handler]] companion object.
""")
trait HandlerConstructor[H] {
  type Env
  type Err
  type In
  type Out

  def toHandler(h: H): Handler[Env, Err, In, Out]
}
object HandlerConstructor extends HandlerConstructorLowPriorityImplicits0 {

  implicit def handlerIsHandlerConstructor[Env0, Err0, In0, Out0]
    : HandlerConstructor.Typed[Handler[Env0, Err0, In0, Out0], Env0, Err0, In0, Out0] =
    new HandlerConstructor[Handler[Env0, Err0, In0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = In0
      type Out = Out0
      type Z   = Handler[Env, Err, In, Out]

      def toHandler(z: Z): Handler[Env, Err, In, Out] =
        z
    }
}

private[http] trait HandlerConstructorLowPriorityImplicits0 extends HandlerConstructorLowPriorityImplicits1 {
  implicit def functionZIOIsHandlerConstructor[Env0, Err0, In0, Out0]
    : HandlerConstructor.Typed[In0 => ZIO[Env0, Err0, Out0], Env0, Err0, In0, Out0] =
    new HandlerConstructor[In0 => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = In0
      type Out = Out0
      type Z   = In0 => ZIO[Env0, Err0, Out0]

      def toHandler(z: Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO(z)
    }

}

private[http] trait HandlerConstructorLowPriorityImplicits1 extends HandlerConstructorLowPriorityImplicits2 {
  implicit def functionIsHandlerConstructor[In0, Out0]: HandlerConstructor.Typed[In0 => Out0, Any, Nothing, In0, Out0] =
    new HandlerConstructor[In0 => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = In0
      type Out = Out0
      type Z   = In0 => Out0

      def toHandler(z: Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction(z)
    }

}

private[http] trait HandlerConstructorLowPriorityImplicits2 extends HandlerConstructorLowPriorityImplicits3 {
  implicit def outZIOIsHandlerConstructor[Env0, Err0, Out0]
    : HandlerConstructor.Typed[ZIO[Env0, Err0, Out0], Env0, Err0, Any, Out0] =
    new HandlerConstructor[ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = Any
      type Out = Out0
      type Z   = ZIO[Env0, Err0, Out0]

      def toHandler(z: Z): Handler[Env, Err, Any, Out] =
        Handler.fromZIO(z)
    }
}

private[http] trait HandlerConstructorLowPriorityImplicits3 {
  type Typed[H, Env0, Err0, In0, Out0] = HandlerConstructor[H] {
    type Env = Env0; type Err = Err0; type In = In0; type Out = Out0
  }

  implicit def responseIsHandlerConstructor: HandlerConstructor.Typed[Response, Any, Nothing, Any, Response] =
    new HandlerConstructor[Response] {
      type Env = Any
      type Err = Nothing
      type In  = Any
      type Out = Response
      type Z   = Response

      def toHandler(z: Z): Handler[Env, Err, In, Out] =
        Handler.succeed(z)
    }
}
