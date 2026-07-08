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

import scala.annotation.implicitNotFound

/**
  * Internal selector used by `.implement`'s macro to inject a BARE handler
  * return value (of static type `A`) into the `Either[Err, Output]` union
  * representation, WITHOUT the user ever writing `Left`/`Right`.
  *
  * For each return-position leaf of the handler body, the macro emits
  * `EndpointInject.inject[Err, Output](leaf)`. Implicit resolution then picks
  * `injectErr` when the leaf conforms to `Err` (→ `Left`) or `injectOutput`
  * when it conforms to `Output` (→ `Right`). Because this resolution happens at
  * the handler's real typecheck (where the lambda parameter is in scope), leaves
  * such as `input.length` classify correctly — something isolated macro-side
  * `typecheck` calls cannot do.
  *
  * `injectErr` is prioritized over `injectOutput` (via the subclass/`LowPriority`
  * split) only to break ties; endpoints whose `Err` and `Output` are the same
  * type cannot be disambiguated by a bare value and must return distinct types.
  */
@implicitNotFound(
  "Handler return value of type ${A} does not conform to the endpoint error type " +
    "${Err} or output type ${Out}. Return a bare Err value or a bare Output value."
)
sealed trait EndpointInject[A, Err, Out] {
  def apply(a: A): Either[Err, Out]
}

object EndpointInject extends EndpointInjectLowPriority {

  def inject[Err, Out]: InjectBuilder[Err, Out] = new InjectBuilder[Err, Out]

  final class InjectBuilder[Err, Out] {
    def apply[A](a: A)(implicit ev: EndpointInject[A, Err, Out]): Either[Err, Out] = ev(a)
  }

  implicit def injectErr[A, Err >: A, Out]: EndpointInject[A, Err, Out] =
    new EndpointInject[A, Err, Out] {
      def apply(a: A): Either[Err, Out] = Left(a)
    }
}

private[endpoint] trait EndpointInjectLowPriority {

  implicit def injectOutput[A, Err, Out >: A]: EndpointInject[A, Err, Out] =
    new EndpointInject[A, Err, Out] {
      def apply(a: A): Either[Err, Out] = Right(a)
    }
}
