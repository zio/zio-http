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

package zio.http.headers

import zio.Scope
import zio.test.{Spec, TestEnvironment, assertTrue}

import zio.http.Header.UserAgent
import zio.http.ZIOHttpSpec

object UserAgentSpec extends ZIOHttpSpec {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("UserAgent suite")(
      test("UserAgent should be parsed correctly") {
        val userAgent =
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36"
        assertTrue(
          UserAgent.parse(userAgent) == Right(
            UserAgent.Complete(
              UserAgent.Product("Mozilla", Some("5.0")),
              Some(
                UserAgent.Comment(
                  " (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
                ),
              ),
            ),
          ),
        )
      },
      test("UserAgent should be rendered correctly") {
        val userAgent = UserAgent.Complete(
          UserAgent.Product("Mozilla", Some("5.0")),
          Some(
            UserAgent.Comment(
              "Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
            ),
          ),
        )
        assertTrue(
          UserAgent.render(
            userAgent,
          ) == "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36)",
        )
      },
    )

}
