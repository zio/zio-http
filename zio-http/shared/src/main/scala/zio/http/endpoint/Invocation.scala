/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.http.{Request, URL}

/**
 * An invocation represents a single invocation of an endpoint through provision
 * of the input that the endpoint requires.
 *
 * Invocations are pure data. In order to be useful, you must execute an
 * invocation with an [[EndpointExecutor]].
 */
final case class Invocation[P, I, E, O, A <: AuthType](
  endpoint: Endpoint[P, I, E, O, A],
  input: I,
) {
  /**
  * Creates a relative URL for this invocation.
  */
  def toURL: Either[String, URL] =
    endpoint.toURL(input)

  /**
   * Creates an absolute URL for this invocation, deriving the scheme and
   * authority from the provided Request.
   */
  def toAbsoluteURL(request: Request): Either[String, URL] =
    endpoint.toAbsoluteURL(input)(request)
}