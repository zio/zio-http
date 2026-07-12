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

import zio.http.ResultType._
import zio.http.schema.{HeaderCodec, QueryCodec}

@implicitNotFound("""
The type ${H} cannot be converted into a zio.http.Handler.

Supported shapes in v4 include:
  - Handler[Ctx, Vars]
  - Response
  - Halt
  - Request => Response | Halt
  - () => Response | Halt
""")
trait ToHandler[H] {
  type Ctx
  type Vars

  def toHandler(h: H): Handler[Ctx, Vars]
}

object ToHandler {
  type Aux[H, Ctx0, Vars0] = ToHandler[H] { type Ctx = Ctx0; type Vars = Vars0 }

  implicit def handlerIsHandler[Ctx0, Vars0]: Aux[Handler[Ctx0, Vars0], Ctx0, Vars0] =
    new ToHandler[Handler[Ctx0, Vars0]] {
      type Ctx  = Ctx0
      type Vars = Vars0

      override def toHandler(h: Handler[Ctx0, Vars0]): Handler[Ctx, Vars] = h
    }

  implicit val responseIsHandler: Aux[Response, Any, Any] =
    new ToHandler[Response] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: Response): Handler[Ctx, Vars] = Handler.succeed(h)
    }

  implicit val haltIsHandler: Aux[Halt, Any, Any] =
    new ToHandler[Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: Halt): Handler[Ctx, Vars] =
        Handler.fromRequest(_ => haltAsResult(h))
    }

  implicit val resultIsHandler: Aux[Response | Halt, Any, Any] =
    new ToHandler[Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: Response | Halt): Handler[Ctx, Vars] = Handler.fromRequest(_ => h)
    }

  implicit def requestFunctionIsHandler: Aux[Request => Response | Halt, Any, Any] =
    new ToHandler[Request => Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: Request => Response | Halt): Handler[Ctx, Vars] =
        Handler.fromRequest(request => h(request))
    }

  implicit def thunkIsHandler: Aux[() => Response | Halt, Any, Any] =
    new ToHandler[() => Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: () => Response | Halt): Handler[Ctx, Vars] =
        Handler.fromRequest(_ => h())
    }

  implicit def decodeQueryFunctionIsHandler[A](implicit queryCodec: QueryCodec[A]): Aux[DecodeQuery[A] => Response | Halt, Any, Any] =
    new ToHandler[DecodeQuery[A] => Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: DecodeQuery[A] => Response | Halt): Handler[Ctx, Vars] =
        Handler.fromRequest { request =>
          DecodeQuery.decode[A](request) match {
            case Right(query) => h(query)
            case Left(halt)   => haltAsResult(halt)
          }
        }
    }

  implicit def decodeHeadersFunctionIsHandler[A](implicit headerCodec: HeaderCodec[A]): Aux[DecodeHeaders[A] => Response | Halt, Any, Any] =
    new ToHandler[DecodeHeaders[A] => Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: DecodeHeaders[A] => Response | Halt): Handler[Ctx, Vars] =
        Handler.fromRequest { request =>
          DecodeHeaders.decode[A](request) match {
            case Right(headers) => h(headers)
            case Left(halt)     => haltAsResult(halt)
          }
        }
    }

  implicit def requestDecodeQueryFunctionIsHandler[A](implicit
    queryCodec: QueryCodec[A],
  ): Aux[(Request, DecodeQuery[A]) => Response | Halt, Any, Any] =
    new ToHandler[(Request, DecodeQuery[A]) => Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: (Request, DecodeQuery[A]) => Response | Halt): Handler[Ctx, Vars] =
        Handler.fromRequest { request =>
          DecodeQuery.decode[A](request) match {
            case Right(query) => h(request, query)
            case Left(halt)   => haltAsResult(halt)
          }
        }
    }

  implicit def requestDecodeHeadersFunctionIsHandler[A](implicit
    headerCodec: HeaderCodec[A],
  ): Aux[(Request, DecodeHeaders[A]) => Response | Halt, Any, Any] =
    new ToHandler[(Request, DecodeHeaders[A]) => Response | Halt] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: (Request, DecodeHeaders[A]) => Response | Halt): Handler[Ctx, Vars] =
        Handler.fromRequest { request =>
          DecodeHeaders.decode[A](request) match {
            case Right(headers) => h(request, headers)
            case Left(halt)     => haltAsResult(halt)
          }
        }
    }
}
