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

package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object RangeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Range suite")(
    test("parsing of invalid Range values") {
      assertTrue(Range.parse("").isLeft, Range.parse("something").isLeft)
    },
    test("parsing and encoding is symmetrical") {
      val value = Range.Single("bytes", 0L, Some(100L))
      assertTrue(Range.parse(Range.render(value)) == Right(value))
    },
    test("parsing of valid Range values") {
      assertTrue(
        Range.parse("bytes=0-100") == Right(Range.Single("bytes", 0L, Some(100L))),
        Range.parse("bytes=0-") == Right(Range.Single("bytes", 0L, None)),
        Range.parse("bytes=0-100,200-300") == Right(
          Range.Multiple("bytes", List((0L, Some(100L)), (200L, Some(300L)))),
        ),
        Range.parse("bytes=0-100,200-") == Right(Range.Multiple("bytes", List((0L, Some(100L)), (200L, None)))),
        Range.parse("bytes=-100") == Right(Range.Suffix("bytes", 100L)),
      )
    },
    test("render Range values") {
      assertTrue(
        Range.render(Range.Single("bytes", 0L, Some(100L))) == "bytes=0-100",
        Range.render(Range.Single("bytes", 0L, None)) == "bytes=0-",
        Range.render(
          Range.Multiple("bytes", List((0L, Some(100L)), (200L, Some(300L)))),
        ) == "bytes=0-100,200-300",
        Range.render(Range.Multiple("bytes", List((0L, Some(100L)), (200L, None)))) == "bytes=0-100,200-",
        Range.render(Range.Suffix("bytes", 100L)) == "bytes=-100",
      )
    },
  )
}
