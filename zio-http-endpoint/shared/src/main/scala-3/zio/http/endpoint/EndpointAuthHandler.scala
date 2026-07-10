/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

import zio.blocks.endpoint.AuthType
import zio.http.{Request, Response}

trait EndpointAuthHandler[Auth <: AuthType, Session] {
  def authenticate(request: Request, auth: Auth): Either[Response, Session]
}

object EndpointAuthHandler {

  def apply[Auth <: AuthType, Session](implicit
    handler: EndpointAuthHandler[Auth, Session],
  ): EndpointAuthHandler[Auth, Session] = handler

  given none: EndpointAuthHandler[AuthType.None.type, Unit] with {
    def authenticate(request: Request, auth: AuthType.None.type): Either[Response, Unit] = Right(())
  }

  def fromValidation[Auth <: AuthType, Session](
    validate: (Request, Auth) => Either[Response, Session],
  ): EndpointAuthHandler[Auth, Session] =
    new EndpointAuthHandler[Auth, Session] {
      def authenticate(request: Request, auth: Auth): Either[Response, Session] = validate(request, auth)
    }
}
