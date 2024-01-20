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

import zio.test._
import zio.{Exit, ZIO}

private[zio] trait ExitAssertion {
  def isDie[R, E, A](ass: Assertion[Throwable]): Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isDie") {
      case Exit.Failure(cause) => cause.dieOption.fold(false)(ass.test)
      case _                   => false
    }

  def isEffect[R, E, A]: Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isEffect") {
      case _: Exit[_, _] => false
      case _             => true
    }

  def isSuccess[R, E, A](ass: Assertion[A]): Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isSuccess") {
      case Exit.Success(a) => ass.test(a)
      case _               => false
    }

  def isFailure[R, E, A](ass: Assertion[E]): Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isFailure") {
      case Exit.Failure(cause) => cause.failureOption.fold(false)(ass.test)
      case _                   => false
    }

  private[zio] implicit class ZIOSyntax[R, E, A](result: ZIO[R, E, A]) {
    def ===(assertion: Assertion[ZIO[R, E, A]]): TestResult = assert(result)(assertion)
  }
}
