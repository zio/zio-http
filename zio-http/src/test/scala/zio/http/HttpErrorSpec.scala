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

import zio.test.Assertion.equalTo
import zio.test.{ZIOSpecDefault, assert}

object HttpErrorSpec extends ZIOSpecDefault {
  def spec = suite("HttpError")(
    suite("foldCause")(
      test("should fold the cause") {
        val error  = HttpError.InternalServerError(cause = Option(new Error("Internal server error")))
        val result = error.foldCause("")(cause => cause.getMessage)
        assert(result)(equalTo("Internal server error"))
      },
      test("should fold with no cause") {
        val error  = HttpError.NotFound(Root.encode)
        val result = error.foldCause("Page not found")(cause => cause.getMessage)
        assert(result)(equalTo("Page not found"))
      },
      test("should create custom error") {
        val error = HttpError.Custom(451, "Unavailable for legal reasons.")
        assert(error.status)(equalTo(Status.Custom(451)))
      },
    ),
  )
}
