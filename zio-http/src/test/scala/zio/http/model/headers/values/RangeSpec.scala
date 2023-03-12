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
      assertTrue(Range.toRange("") == Range.InvalidRange) &&
      assertTrue(Range.toRange("something") == Range.InvalidRange)
    },
    test("parsing and encoding is symmetrical") {
      val value = Range.SingleRange("bytes", 0L, Some(100L))
      assertTrue(Range.toRange(Range.fromRange(value)) == value)
    },
    test("parsing of valid Range values") {
      assertTrue(Range.toRange("bytes=0-100") == Range.SingleRange("bytes", 0L, Some(100L))) &&
      assertTrue(Range.toRange("bytes=0-") == Range.SingleRange("bytes", 0L, None)) &&
      assertTrue(
        Range.toRange("bytes=0-100,200-300") == Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, Some(300L)))),
      ) &&
      assertTrue(
        Range.toRange("bytes=0-100,200-") == Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, None))),
      ) &&
      assertTrue(Range.toRange("bytes=-100") == Range.SuffixRange("bytes", 100L))
    },
    test("render Range values") {
      assertTrue(Range.fromRange(Range.SingleRange("bytes", 0L, Some(100L))) == "bytes=0-100") &&
      assertTrue(Range.fromRange(Range.SingleRange("bytes", 0L, None)) == "bytes=0-") &&
      assertTrue(
        Range.fromRange(
          Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, Some(300L)))),
        ) == "bytes=0-100,200-300",
      ) &&
      assertTrue(
        Range.fromRange(Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, None)))) == "bytes=0-100,200-",
      ) &&
      assertTrue(Range.fromRange(Range.SuffixRange("bytes", 100L)) == "bytes=-100")
    },
  )
}
