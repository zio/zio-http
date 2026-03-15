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

@implicitNotFound("""
Cannot serve routes with unhandled error type ${Err}.

Server.serve requires all errors to be handled (error type must be Response).
To fix this, handle your errors before serving:

  - routes.handleError(err => Response.internalServerError(err.toString))
  - routes.sandbox  // converts all errors to 500 responses
  - route.handleError(...)  // handle per-route

Your current error type is: ${Err}
""")
sealed trait CanServe[-Err] {
  private[http] def toResponse[R](routes: Routes[R, Err]): Routes[R, Response]
}

object CanServe {
  implicit val canServeResponse: CanServe[Response] = new CanServe[Response] {
    override private[http] def toResponse[R](routes: Routes[R, Response]): Routes[R, Response] = routes
  }
  implicit val canServeNothing: CanServe[Nothing]   = new CanServe[Nothing] {
    override private[http] def toResponse[R](routes: Routes[R, Nothing]): Routes[R, Response] =
      routes.asInstanceOf[Routes[R, Response]]
  }
}
