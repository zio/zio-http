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

package zio.http

import scala.annotation.implicitNotFound
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * IsMono is a type-constraint that is used by the middleware api for allowing
 * some operators only when the following condition is met.
 *
 * Condition: Since a middleware takes in an Http and returns a new Http,
 * IsMono, makes sure that the type parameters of the incoming Http and the ones
 * for the outgoing Http is the same.
 *
 * For Eg: IsMono will be defined for a middleware that looks as follows
 * ```
 * val mid: Middleware[Any, Nothing, Request, Response, Request, Response]
 * ```
 *
 * This is because both the middleware is defined from (Request, Response) =>
 * (Request, Response). Consider another example:
 *
 * ```
 * val mid: Middleware[Any, Nothing, Request, Response, UserRequest, UserResponse]
 * ```
 *
 * In this case, the incoming and outgoing types are different viz. (Request,
 * Response) => (UserRequest, UserResponse), hence there is no IsMono defined
 * for such middlewares.
 */
@implicitNotFound(
  "This operation is only valid if the incoming and outgoing type are same.",
)
sealed trait IsMono[-AIn, +AOut, +BIn, -BOut] {}

object IsMono extends IsMono[Any, Nothing, Nothing, Any] {
  implicit def mono[AIn, AOut, BIn, BOut](implicit a: AIn =:= BIn, b: AOut =:= BOut): IsMono[AIn, AOut, BIn, BOut] =
    IsMono
}
