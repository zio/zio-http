/*
 * Copyright 2023 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import scala.annotation.implicitNotFound
import scala.util.Try

import zio.{Exit, ZIO}

@implicitNotFound("""
The type ${H} does not appear to be one that can be used to construct a handler. The following types may be used to construct handlers:

 - Handlers:          Handler[Env, Err, In, Out]
 - Responses:         Response
 - ZIO Values:        ZIO[Env, Err, Out]
 - Exit Values:       Exit[Err, Out]
 - Simple Functions:  In => Out
 - ZIO Functions:     In => ZIO[Env, Err, Out]
 - ???:               Nothing

If you constructing a handler from a function, the types to the function must be specified explicitly: Scala cannot infer them due to the smart constructor.

If you are having trouble using this smart constructor function, you can always make a handler directly with the constructors on the [[zio.http.Handler]] companion object.
""")
trait ToHandler[H] {
  type Env
  type Err
  type In
  type Out

  def toHandler(h: => H): Handler[Env, Err, In, Out]
}
object ToHandler extends HandlerConstructorLowPriorityImplicits0 {

  implicit def nothingIsHandlerConstructor: ToHandler.Typed[Nothing, Any, Nothing, Any, Nothing] =
    new ToHandler[Nothing] {
      type Env = Any
      type Err = Nothing
      type In  = Any
      type Out = Nothing
      type Z   = Nothing

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction[Any](_ => z)
    }
}

private[http] trait HandlerConstructorLowPriorityImplicits0 extends HandlerConstructorLowPriorityImplicits1 {

  implicit def handlerIsHandlerConstructor[Env0, Err0, In0, Out0]
    : ToHandler.Typed[Handler[Env0, Err0, In0, Out0], Env0, Err0, In0, Out0] =
    new ToHandler[Handler[Env0, Err0, In0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = In0
      type Out = Out0
      type Z   = Handler[Env, Err, In, Out]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        z
    }

}

private[http] trait HandlerConstructorLowPriorityImplicits1 extends HandlerConstructorLowPriorityImplicits2 {
  implicit def function2ZIOIsHandlerConstructor[Env0, Err0, In1, In2, Out0]
    : ToHandler.Typed[(In1, In2) => ZIO[Env0, Err0, Out0], Env0, Err0, (In1, In2), Out0] =
    new ToHandler[(In1, In2) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2)
      type Out = Out0
      type Z   = (In1, In2) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2) => z(in1, in2) }
    }

  implicit def function3ZIOIsHandlerConstructor[Env0, Err0, In1, In2, In3, Out0]
    : ToHandler.Typed[(In1, In2, In3) => ZIO[Env0, Err0, Out0], Env0, Err0, (In1, In2, In3), Out0] =
    new ToHandler[(In1, In2, In3) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3)
      type Out = Out0
      type Z   = (In1, In2, In3) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2, in3) => z(in1, in2, in3) }
    }

  implicit def function4ZIOIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4) => ZIO[Env0, Err0, Out0], Env0, Err0, (In1, In2, In3, In4), Out0] =
    new ToHandler[(In1, In2, In3, In4) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4)
      type Out = Out0
      type Z   = (In1, In2, In3, In4) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2, in3, in4) => z(in1, in2, in3, in4) }
    }

  implicit def function5ZIOIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5) => ZIO[
      Env0,
      Err0,
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5), Out0] =
    new ToHandler[(In1, In2, In3, In4, In5) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2, in3, in4, in5) => z(in1, in2, in3, in4, in5) }
    }

  implicit def function6ZIOIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, In6, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5, In6) => ZIO[
      Env0,
      Err0,
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5, In6), Out0] =
    new ToHandler[(In1, In2, In3, In4, In5, In6) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5, In6)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2, in3, in4, in5, in6) => z(in1, in2, in3, in4, in5, in6) }
    }

  implicit def function7ZIOIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, In6, In7, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5, In6, In7) => ZIO[
      Env0,
      Err0,
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5, In6, In7), Out0] =
    new ToHandler[(In1, In2, In3, In4, In5, In6, In7) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5, In6, In7)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6, In7) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2, in3, in4, in5, in6, in7) => z(in1, in2, in3, in4, in5, in6, in7) }
    }

  implicit def function8ZIOIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, In6, In7, In8, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5, In6, In7, In8) => ZIO[
      Env0,
      Err0,
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5, In6, In7, In8), Out0] =
    new ToHandler[(In1, In2, In3, In4, In5, In6, In7, In8) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5, In6, In7, In8)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6, In7, In8) => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO { case (in1, in2, in3, in4, in5, in6, in7, in8) =>
          z(in1, in2, in3, in4, in5, in6, in7, in8)
        }
    }

  implicit def function2HandlerIsHandlerConstructor[Env0, Err0, In1, In2, Out0]
    : ToHandler.Typed[(In1, In2) => Handler[Env0, Err0, (In1, In2), Out0], Env0, Err0, (In1, In2), Out0] =
    new ToHandler[(In1, In2) => Handler[Env0, Err0, (In1, In2), Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2)
      type Out = Out0
      type Z   = (In1, In2) => Handler[Env0, Err0, (In1, In2), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2) => z(in1, in2) }
    }

  implicit def function3HandlerIsHandlerConstructor[Env0, Err0, In1, In2, In3, Out0]: ToHandler.Typed[
    (In1, In2, In3) => Handler[Env0, Err0, (In1, In2, In3), Out0],
    Env0,
    Err0,
    (In1, In2, In3),
    Out0,
  ] =
    new ToHandler[(In1, In2, In3) => Handler[Env0, Err0, (In1, In2, In3), Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3)
      type Out = Out0
      type Z   = (In1, In2, In3) => Handler[Env0, Err0, (In1, In2, In3), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2, in3) => z(in1, in2, in3) }
    }

  implicit def function4HandlerIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, Out0]: ToHandler.Typed[
    (In1, In2, In3, In4) => Handler[Env0, Err0, (In1, In2, In3, In4), Out0],
    Env0,
    Err0,
    (In1, In2, In3, In4),
    Out0,
  ] =
    new ToHandler[(In1, In2, In3, In4) => Handler[Env0, Err0, (In1, In2, In3, In4), Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4)
      type Out = Out0
      type Z   = (In1, In2, In3, In4) => Handler[Env0, Err0, (In1, In2, In3, In4), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2, in3, in4) => z(in1, in2, in3, in4) }
    }

  implicit def function5HandlerIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5) => Handler[
      Env0,
      Err0,
      (In1, In2, In3, In4, In5),
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5), Out0] =
    new ToHandler[(In1, In2, In3, In4, In5) => Handler[Env0, Err0, (In1, In2, In3, In4, In5), Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5) => Handler[Env0, Err0, (In1, In2, In3, In4, In5), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2, in3, in4, in5) => z(in1, in2, in3, in4, in5) }
    }

  implicit def function6HandlerIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, In6, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5, In6) => Handler[
      Env0,
      Err0,
      (In1, In2, In3, In4, In5, In6),
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5, In6), Out0] =
    new ToHandler[
      (In1, In2, In3, In4, In5, In6) => Handler[Env0, Err0, (In1, In2, In3, In4, In5, In6), Out0],
    ] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5, In6)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6) => Handler[Env0, Err0, (In1, In2, In3, In4, In5, In6), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2, in3, in4, in5, in6) => z(in1, in2, in3, in4, in5, in6) }
    }

  implicit def function7HandlerIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, In6, In7, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5, In6, In7) => Handler[
      Env0,
      Err0,
      (In1, In2, In3, In4, In5, In6, In7),
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5, In6, In7), Out0] =
    new ToHandler[
      (In1, In2, In3, In4, In5, In6, In7) => Handler[Env0, Err0, (In1, In2, In3, In4, In5, In6, In7), Out0],
    ] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5, In6, In7)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6, In7) => Handler[Env0, Err0, (In1, In2, In3, In4, In5, In6, In7), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2, in3, in4, in5, in6, in7) => z(in1, in2, in3, in4, in5, in6, in7) }
    }

  implicit def function8HandlerIsHandlerConstructor[Env0, Err0, In1, In2, In3, In4, In5, In6, In7, In8, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5, In6, In7, In8) => Handler[
      Env0,
      Err0,
      (In1, In2, In3, In4, In5, In6, In7, In8),
      Out0,
    ], Env0, Err0, (In1, In2, In3, In4, In5, In6, In7, In8), Out0] =
    new ToHandler[
      (In1, In2, In3, In4, In5, In6, In7, In8) => Handler[Env0, Err0, (In1, In2, In3, In4, In5, In6, In7, In8), Out0],
    ] {
      type Env = Env0
      type Err = Err0
      type In  = (In1, In2, In3, In4, In5, In6, In7, In8)
      type Out = Out0
      type Z   =
        (In1, In2, In3, In4, In5, In6, In7, In8) => Handler[Env0, Err0, (In1, In2, In3, In4, In5, In6, In7, In8), Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionHandler { case (in1, in2, in3, in4, in5, in6, in7, in8) =>
          z(in1, in2, in3, in4, in5, in6, in7, in8)
        }
    }

  implicit def function2ResponseIsHandlerConstructor[In1, In2]
    : ToHandler.Typed[(In1, In2) => Response, Any, Nothing, (In1, In2), Response] =
    new ToHandler[(In1, In2) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2)
      type Out = Response
      type Z   = (In1, In2) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2) => z(in1, in2) }
    }

  implicit def function3ResponseIsHandlerConstructor[In1, In2, In3]
    : ToHandler.Typed[(In1, In2, In3) => Response, Any, Nothing, (In1, In2, In3), Response] =
    new ToHandler[(In1, In2, In3) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3)
      type Out = Response
      type Z   = (In1, In2, In3) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3) => z(in1, in2, in3) }
    }

  implicit def function4ResponseIsHandlerConstructor[In1, In2, In3, In4]
    : ToHandler.Typed[(In1, In2, In3, In4) => Response, Any, Nothing, (In1, In2, In3, In4), Response] =
    new ToHandler[(In1, In2, In3, In4) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4)
      type Out = Response
      type Z   = (In1, In2, In3, In4) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4) => z(in1, in2, in3, in4) }
    }

  implicit def function5ResponseIsHandlerConstructor[In1, In2, In3, In4, In5]: ToHandler.Typed[
    (In1, In2, In3, In4, In5) => Response,
    Any,
    Nothing,
    (In1, In2, In3, In4, In5),
    Response,
  ] =
    new ToHandler[(In1, In2, In3, In4, In5) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5)
      type Out = Response
      type Z   = (In1, In2, In3, In4, In5) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5) => z(in1, in2, in3, in4, in5) }
    }

  implicit def function6ResponseIsHandlerConstructor[In1, In2, In3, In4, In5, In6]: ToHandler.Typed[
    (In1, In2, In3, In4, In5, In6) => Response,
    Any,
    Nothing,
    (In1, In2, In3, In4, In5, In6),
    Response,
  ] =
    new ToHandler[(In1, In2, In3, In4, In5, In6) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5, In6)
      type Out = Response
      type Z   = (In1, In2, In3, In4, In5, In6) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5, in6) => z(in1, in2, in3, in4, in5, in6) }
    }

  implicit def function7ResponseIsHandlerConstructor[In1, In2, In3, In4, In5, In6, In7]: ToHandler.Typed[
    (In1, In2, In3, In4, In5, In6, In7) => Response,
    Any,
    Nothing,
    (In1, In2, In3, In4, In5, In6, In7),
    Response,
  ] =
    new ToHandler[(In1, In2, In3, In4, In5, In6, In7) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5, In6, In7)
      type Out = Response
      type Z   = (In1, In2, In3, In4, In5, In6, In7) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5, in6, in7) => z(in1, in2, in3, in4, in5, in6, in7) }
    }

  implicit def function8ResponseIsHandlerConstructor[In1, In2, In3, In4, In5, In6, In7, In8]: ToHandler.Typed[
    (In1, In2, In3, In4, In5, In6, In7, In8) => Response,
    Any,
    Nothing,
    (In1, In2, In3, In4, In5, In6, In7, In8),
    Response,
  ] =
    new ToHandler[(In1, In2, In3, In4, In5, In6, In7, In8) => Response] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5, In6, In7, In8)
      type Out = Response
      type Z   = (In1, In2, In3, In4, In5, In6, In7, In8) => Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5, in6, in7, in8) =>
          z(in1, in2, in3, in4, in5, in6, in7, in8)
        }
    }
}

private[http] trait HandlerConstructorLowPriorityImplicits2 extends HandlerConstructorLowPriorityImplicits3 {
  implicit def functionZIOIsHandlerConstructor[Env0, Err0, In0, Out0]
    : ToHandler.Typed[In0 => ZIO[Env0, Err0, Out0], Env0, Err0, In0, Out0] =
    new ToHandler[In0 => ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = In0
      type Out = Out0
      type Z   = In0 => ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunctionZIO(z)
    }
}

private[http] trait HandlerConstructorLowPriorityImplicits3 extends HandlerConstructorLowPriorityImplicits4 {
  implicit def function2IsHandlerConstructor[In1, In2, Out0]
    : ToHandler.Typed[(In1, In2) => Out0, Any, Nothing, (In1, In2), Out0] =
    new ToHandler[(In1, In2) => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2)
      type Out = Out0
      type Z   = (In1, In2) => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2) => z(in1, in2) }
    }

  implicit def function3IsHandlerConstructor[In1, In2, In3, Out0]
    : ToHandler.Typed[(In1, In2, In3) => Out0, Any, Nothing, (In1, In2, In3), Out0] =
    new ToHandler[(In1, In2, In3) => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3)
      type Out = Out0
      type Z   = (In1, In2, In3) => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3) => z(in1, in2, in3) }
    }

  implicit def function4IsHandlerConstructor[In1, In2, In3, In4, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4) => Out0, Any, Nothing, (In1, In2, In3, In4), Out0] =
    new ToHandler[(In1, In2, In3, In4) => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4)
      type Out = Out0
      type Z   = (In1, In2, In3, In4) => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4) => z(in1, in2, in3, in4) }
    }

  implicit def function5IsHandlerConstructor[In1, In2, In3, In4, In5, Out0]
    : ToHandler.Typed[(In1, In2, In3, In4, In5) => Out0, Any, Nothing, (In1, In2, In3, In4, In5), Out0] =
    new ToHandler[(In1, In2, In3, In4, In5) => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5) => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5) => z(in1, in2, in3, in4, in5) }
    }

  implicit def function6IsHandlerConstructor[In1, In2, In3, In4, In5, In6, Out0]: ToHandler.Typed[
    (In1, In2, In3, In4, In5, In6) => Out0,
    Any,
    Nothing,
    (In1, In2, In3, In4, In5, In6),
    Out0,
  ] =
    new ToHandler[(In1, In2, In3, In4, In5, In6) => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5, In6)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6) => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5, in6) => z(in1, in2, in3, in4, in5, in6) }
    }

  implicit def function7IsHandlerConstructor[In1, In2, In3, In4, In5, In6, In7, Out0]: ToHandler.Typed[
    (In1, In2, In3, In4, In5, In6, In7) => Out0,
    Any,
    Nothing,
    (In1, In2, In3, In4, In5, In6, In7),
    Out0,
  ] =
    new ToHandler[(In1, In2, In3, In4, In5, In6, In7) => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = (In1, In2, In3, In4, In5, In6, In7)
      type Out = Out0
      type Z   = (In1, In2, In3, In4, In5, In6, In7) => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction { case (in1, in2, in3, in4, in5, in6, in7) => z(in1, in2, in3, in4, in5, in6, in7) }
    }
}

private[http] trait HandlerConstructorLowPriorityImplicits4 {
  type Typed[H, Env0, Err0, In0, Out0] = ToHandler[H] {
    type Env = Env0; type Err = Err0; type In = In0; type Out = Out0
  }

  implicit def functionIsHandlerConstructor[In0, Out0]: ToHandler.Typed[In0 => Out0, Any, Nothing, In0, Out0] =
    new ToHandler[In0 => Out0] {
      type Env = Any
      type Err = Nothing
      type In  = In0
      type Out = Out0
      type Z   = In0 => Out0

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromFunction(z)
    }

  implicit def zioIsHandlerConstructor[Env0, Err0, Out0]
    : ToHandler.Typed[ZIO[Env0, Err0, Out0], Env0, Err0, Any, Out0] =
    new ToHandler[ZIO[Env0, Err0, Out0]] {
      type Env = Env0
      type Err = Err0
      type In  = Any
      type Out = Out0
      type Z   = ZIO[Env0, Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, Any, Out] =
        Handler.fromZIO(z)
    }

  implicit def responseIsHandlerConstructor: ToHandler.Typed[Response, Any, Nothing, Any, Response] =
    new ToHandler[Response] {
      type Env = Any
      type Err = Nothing
      type In  = Any
      type Out = Response
      type Z   = Response

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.succeed(z)
    }

  implicit def exitIsHandlerConstructor[E, A]: ToHandler.Typed[Exit[E, A], Any, E, Any, A] =
    new ToHandler[Exit[E, A]] {
      type Env = Any
      type Err = E
      type In  = Any
      type Out = A
      type Z   = Exit[E, A]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromExit(z)
    }

  implicit def eitherIsHandlerConstructor[Err0, Out0]: ToHandler.Typed[Either[Err0, Out0], Any, Err0, Any, Out0] =
    new ToHandler[Either[Err0, Out0]] {
      type Env = Any
      type Err = Err0
      type In  = Any
      type Out = Out0
      type Z   = Either[Err0, Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromEither(z)
    }

  implicit def tryIsHandlerConstructor[Out0]: ToHandler.Typed[Try[Out0], Any, Throwable, Any, Out0] =
    new ToHandler[Try[Out0]] {
      type Env = Any
      type Err = Throwable
      type In  = Any
      type Out = Out0
      type Z   = Try[Out0]

      def toHandler(z: => Z): Handler[Env, Err, In, Out] =
        Handler.fromEither(z.toEither)
    }
}
