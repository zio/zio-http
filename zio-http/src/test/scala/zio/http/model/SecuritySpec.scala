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

package zio.http.model

import zio.test.Assertion.equalTo
import zio.test.{Spec, ZIOSpecDefault, assert}

object SecuritySpec extends ZIOSpecDefault {
  def spec: Spec[Any, Nothing] = suite("HttpError")(
    suite("security")(
      test("should encode HTML output, to protect against XSS") {
        val error = HttpError.NotFound("<script>alert(\"xss\")</script>").message
        assert(error)(
          equalTo(
            "The requested URI \"&lt;script&gt;alert(&quot;xss&quot;)&lt;&#x2F;script&gt;\" was not found on this server\n",
          ),
        )
      },
    ),
  )
}
