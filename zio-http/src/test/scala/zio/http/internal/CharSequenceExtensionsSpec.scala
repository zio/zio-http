package zio.http.internal

import zio.test._

object CharSequenceExtensionsSpec extends ZIOSpecDefault {

  override def spec = suite("CharSequence extensions")(
    test("equals") {
      check(Gen.alphaNumericString, Gen.alphaNumericString) { (input1, input2) =>
        val input1Low = input1.toLowerCase
        assertTrue(
          CharSequenceExtensions.equals(input1, input2, CaseMode.Sensitive) == input1.equals(input2),
          CharSequenceExtensions.equals(input1, input2, CaseMode.Insensitive) == input1.equalsIgnoreCase(input2),
          CharSequenceExtensions.equals(input1, input1Low, CaseMode.Sensitive) == input1.equals(input1Low),
          CharSequenceExtensions.equals(input1, input1Low, CaseMode.Insensitive) == input1.equalsIgnoreCase(input1Low),
          CharSequenceExtensions.equals(input1, input1, CaseMode.Sensitive),
          CharSequenceExtensions.equals(input1, input1, CaseMode.Insensitive),
          CharSequenceExtensions.equals(input2, input2, CaseMode.Sensitive),
          CharSequenceExtensions.equals(input2, input2, CaseMode.Insensitive),
        )
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
