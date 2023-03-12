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

package zio.http.internal

import zio.test._

object CharSequenceExtensionsSpec extends ZIOSpecDefault {

  override def spec = suite("CharSequence extensions")(
    test("equals") {
      check(Gen.string, Gen.string) { (input1, input2) =>
        val checkNotEquals = if (!input1.equalsIgnoreCase(input2)) {
          assertTrue(!CharSequenceExtensions.equals(input1, input2, CaseMode.Sensitive)) &&
          assertTrue(!CharSequenceExtensions.equals(input1, input2, CaseMode.Insensitive))
        } else assertTrue(true)

        checkNotEquals &&
        assertTrue(CharSequenceExtensions.equals(input1, input1, CaseMode.Sensitive)) &&
        assertTrue(CharSequenceExtensions.equals(input1, input1, CaseMode.Insensitive))
      }
    },
    test("compare") {
      check(Gen.alphaNumericString, Gen.alphaNumericString) { (input1, input2) =>
        val expected            = math.signum(input1.compareTo(input2))
        val expectedInsensitive = math.signum(input1.compareToIgnoreCase(input2))
        assertTrue(math.signum(CharSequenceExtensions.compare(input1, input2, CaseMode.Sensitive)) == expected) &&
        assertTrue(
          math.signum(CharSequenceExtensions.compare(input1, input2, CaseMode.Insensitive)) == expectedInsensitive,
        ) &&
        assertTrue(CharSequenceExtensions.compare(input1, input1, CaseMode.Sensitive) == 0) &&
        assertTrue(CharSequenceExtensions.compare(input1, input1, CaseMode.Insensitive) == 0)
      }
    },
    test("hashCode") {
      check(Gen.alphaNumericString) { input =>
        assertTrue(CharSequenceExtensions.hashCode(input) == input.hashCode)
      }
    },
    test("contains") {
      assertTrue(CharSequenceExtensions.contains("", "", CaseMode.Sensitive)) &&
      assertTrue(CharSequenceExtensions.contains("", "", CaseMode.Insensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestString", "", CaseMode.Sensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestString", "", CaseMode.Insensitive)) &&
      assertTrue(!CharSequenceExtensions.contains("", "String", CaseMode.Sensitive)) &&
      assertTrue(!CharSequenceExtensions.contains("", "String", CaseMode.Insensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestString", "String", CaseMode.Sensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestStringTest", "String", CaseMode.Sensitive)) &&
      assertTrue(CharSequenceExtensions.contains("StringTest", "String", CaseMode.Sensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestString", "string", CaseMode.Insensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestStringTest", "string", CaseMode.Insensitive)) &&
      assertTrue(CharSequenceExtensions.contains("StringTest", "string", CaseMode.Insensitive)) &&
      assertTrue(CharSequenceExtensions.contains("TestString", "TestString", CaseMode.Sensitive)) &&
      assertTrue(CharSequenceExtensions.contains("teststring", "TestString", CaseMode.Insensitive))
    },
  )
}
