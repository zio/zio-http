package zio.http.gen.scala

import zio.Scope
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test._

object FieldNormalizationSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("FieldNormalizationSpec")(
      test("Simple lowercase (None signals no change)") {
        assert(Code.Field.normalize("foo"))(isNone)
      },
      test("Simple UPPERCASE") {
        assert(Code.Field.normalize("FOO"))(isSome(equalTo("foo")))
      },
      test("preserve camelCase (None signals no change)") {
        assert(Code.Field.normalize("fooBar"))(isNone)
      },
      test("preserve camelCase with digits (None signals no change)") {
        assert(Code.Field.normalize("fooBar42"))(isNone)
      },
      test("preserve camelCase with digits #2 (None signals no change)") {
        assert(Code.Field.normalize("foo42Bar"))(isNone)
      },
      test("lowercase capitalized camelCase") {
        assert(Code.Field.normalize("FooBar"))(isSome(equalTo("fooBar")))
      },
      test("preserve non-leading UPPERCASE (None signals no change)") {
        assert(Code.Field.normalize("fooBAR"))(isNone)
      },
      test("mixed camelSnake_case") {
        assert(Code.Field.normalize("camelSnake_case"))(isSome(equalTo("camelSnakeCase")))
      },
      test("mixed snake_caseUPPERLower") {
        assert(Code.Field.normalize("ARN_APIGateway"))(isSome(equalTo("arnAPIGateway")))
      },
      test("challenge with complex CamelCase") {
        assert(Code.Field.normalize("UseWD40ToLossenBut3MToFasten"))(isSome(equalTo("useWD40ToLossenBut3MToFasten")))
      },
      test("with whitespaces") {
        assert(Code.Field.normalize("white\tspace - as\nsep"))(isSome(equalTo("whiteSpaceAsSep")))
      },
    )
}
