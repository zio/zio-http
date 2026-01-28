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

import scala.annotation.nowarn

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.URL

/**
 * An endpoint locator is responsible for locating endpoints.
 */
@deprecated(
  "EndpointLocator will be removed in a future release. Please use URLs directly to create an EndpointExecutor",
  "3.6.0",
)
trait EndpointLocator { self =>

  /**
   * Returns the location to the specified endpoint, or fails with an endpoint
   * error.
   */
  def locate[P, A, E, B](api: Endpoint[P, A, E, B, _ <: AuthType])(implicit
    trace: Trace,
  ): IO[EndpointNotFound, URL]

  @nowarn
  final def orElse(that: EndpointLocator): EndpointLocator = new EndpointLocator {
    def locate[P, A, E, B](api: Endpoint[P, A, E, B, _ <: AuthType])(implicit
      trace: Trace,
    ): IO[EndpointNotFound, URL] =
      self.locate(api).orElse(that.locate(api))
  }
}
object EndpointLocator {
  @deprecated(
    "EndpointLocator.fromURL is deprecated and will be removed in a future release. Please use URLs directly to create an EndpointExecutor",
    "3.6.0",
  )
  def fromURL(url: URL)(implicit trace: Trace): EndpointLocator = new EndpointLocator {
    private val effect = ZIO.succeed(url)

    def locate[P, A, E, B](api: Endpoint[P, A, E, B, _ <: AuthType])(implicit
      trace: Trace,
    ): IO[EndpointNotFound, URL] =
      effect
  }
}
