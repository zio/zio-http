package zio.http.gen.smithy

import zio.Scope
import zio.test._

object SmithyCodecsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SmithyCodecsSpec")(
      suite("Identifier Codec")(
        test("parses simple identifier") {
          val result = SmithyCodecs.identifier.decode("myVar")
          assertTrue(result == Right("myVar"))
        },
        test("parses identifier with underscore") {
          val result = SmithyCodecs.identifier.decode("my_var")
          assertTrue(result == Right("my_var"))
        },
        test("parses identifier starting with underscore") {
          val result = SmithyCodecs.identifier.decode("_private")
          assertTrue(result == Right("_private"))
        },
        test("parses identifier with numbers") {
          val result = SmithyCodecs.identifier.decode("var123")
          assertTrue(result == Right("var123"))
        },
        test("fails on identifier starting with digit") {
          val result = SmithyCodecs.identifier.decode("123var")
          assertTrue(result.isLeft)
        },
      ),
      suite("Namespace Codec")(
        test("parses simple namespace") {
          val result = SmithyCodecs.namespace.decode("example")
          assertTrue(result == Right("example"))
        },
        test("parses dotted namespace") {
          val result = SmithyCodecs.namespace.decode("example.api")
          assertTrue(result == Right("example.api"))
        },
        test("parses multi-part namespace") {
          val result = SmithyCodecs.namespace.decode("com.example.weather")
          assertTrue(result == Right("com.example.weather"))
        },
      ),
      suite("QuotedString Codec")(
        test("parses simple string") {
          val result = SmithyCodecs.quotedString.decode("\"hello\"")
          assertTrue(result == Right("hello"))
        },
        test("parses empty string") {
          val result = SmithyCodecs.quotedString.decode("\"\"")
          assertTrue(result == Right(""))
        },
        test("parses string with escape sequences") {
          val result = SmithyCodecs.quotedString.decode("\"hello\\nworld\"")
          assertTrue(result == Right("hello\nworld"))
        },
        test("parses string with escaped quote") {
          val result = SmithyCodecs.quotedString.decode("\"say \\\"hello\\\"\"")
          assertTrue(result == Right("say \"hello\""))
        },
      ),
      suite("Number Codecs")(
        test("parses positive integer") {
          val result = SmithyCodecs.integerNumber.decode("42")
          assertTrue(result == Right(42L))
        },
        test("parses negative integer") {
          val result = SmithyCodecs.integerNumber.decode("-42")
          assertTrue(result == Right(-42L))
        },
        test("parses zero") {
          val result = SmithyCodecs.integerNumber.decode("0")
          assertTrue(result == Right(0L))
        },
        test("parses decimal number") {
          val result = SmithyCodecs.decimalNumber.decode("3.14")
          assertTrue(result == Right(BigDecimal("3.14")))
        },
        test("parses negative decimal") {
          val result = SmithyCodecs.decimalNumber.decode("-2.5")
          assertTrue(result == Right(BigDecimal("-2.5")))
        },
      ),
      suite("ShapeId Codec")(
        test("parses simple shape name") {
          val result = SmithyCodecs.shapeId.decode("String")
          assertTrue(result == Right(ShapeId(None, "String", None)))
        },
        test("parses qualified shape id") {
          val result = SmithyCodecs.shapeId.decode("example.api#MyShape")
          assertTrue(result == Right(ShapeId(Some("example.api"), "MyShape", None)))
        },
        test("parses shape id with member") {
          val result = SmithyCodecs.shapeId.decode("example.api#MyUnion$member1")
          assertTrue(result == Right(ShapeId(Some("example.api"), "MyUnion", Some("member1"))))
        },
        test("parses shape id with member but no namespace") {
          val result = SmithyCodecs.shapeId.decode("MyUnion$member1")
          assertTrue(result == Right(ShapeId(None, "MyUnion", Some("member1"))))
        },
      ),
      suite("NodeValue Codecs")(
        test("parses string node") {
          val result = SmithyCodecs.nodeString.decode("\"hello\"")
          assertTrue(result == Right(NodeValue.Str("hello")))
        },
        test("parses number node") {
          val result = SmithyCodecs.nodeNumber.decode("42")
          assertTrue(result == Right(NodeValue.Num(BigDecimal(42))))
        },
        test("parses true boolean") {
          val result = SmithyCodecs.nodeBool.decode("true")
          assertTrue(result == Right(NodeValue.Bool(true)))
        },
        test("parses false boolean") {
          val result = SmithyCodecs.nodeBool.decode("false")
          assertTrue(result == Right(NodeValue.Bool(false)))
        },
        test("parses null") {
          val result = SmithyCodecs.nodeNull.decode("null")
          assertTrue(result == Right(NodeValue.Null))
        },
      ),
      suite("Simple Shape Keywords")(
        test("parses string keyword") {
          val result = SmithyCodecs.simpleShapeKeyword.decode("string")
          assertTrue(result == Right("string"))
        },
        test("parses integer keyword") {
          val result = SmithyCodecs.simpleShapeKeyword.decode("integer")
          assertTrue(result == Right("integer"))
        },
        test("parses boolean keyword") {
          val result = SmithyCodecs.simpleShapeKeyword.decode("boolean")
          assertTrue(result == Right("boolean"))
        },
        test("parses timestamp keyword") {
          val result = SmithyCodecs.simpleShapeKeyword.decode("timestamp")
          assertTrue(result == Right("timestamp"))
        },
      ),
    )
}
