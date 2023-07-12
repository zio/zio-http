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

import zio.Zippable

@implicitNotFound("""
Your request handler is required to accept both parameters ${A}, as well as the incoming [[zio.http.Request]].
This is true even if you wish to ignore some parameters or the request itself. Try to add missing parameters 
until you no longer receive this error message. If all else fails, you can construct a handler manually using 
the constructors in the companion object of [[zio.http.Handler]] using the precise type.""")
final class RequestHandlerInput[A, I](val zippable: Zippable.Out[A, Request, I])
object RequestHandlerInput {
  implicit def apply[A, I](implicit zippable: Zippable.Out[A, Request, I]): RequestHandlerInput[A, I] =
    new RequestHandlerInput(zippable)
}
