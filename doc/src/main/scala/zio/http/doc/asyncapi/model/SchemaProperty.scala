package zio.http.doc.asyncapi.model

sealed abstract case class SchemaProperty(value: String) {
  override def toString: String = value
}

object SchemaProperty {
  final object Title                extends SchemaProperty("title")
  final object Type                 extends SchemaProperty("type")
  final object Required             extends SchemaProperty("required")
  final object MultipleOf           extends SchemaProperty("multipleOf")
  final object Maximum              extends SchemaProperty("maximum")
  final object ExclusiveMaximum     extends SchemaProperty("exclusiveMaximum")
  final object Minimum              extends SchemaProperty("minimum")
  final object ExclusiveMinimum     extends SchemaProperty("exclusiveMinimum")
  final object MaxLength            extends SchemaProperty("maxLength")
  final object MinLength            extends SchemaProperty("minLength")
  final object Pattern              extends SchemaProperty("pattern")
  final object MaxItems             extends SchemaProperty("maxItems")
  final object MinItems             extends SchemaProperty("minItems")
  final object UniqueItems          extends SchemaProperty("uniqueItems")
  final object MaxProperties        extends SchemaProperty("maxProperties")
  final object MinProperties        extends SchemaProperty("minProperties")
  final object Enum                 extends SchemaProperty("enum")
  final object Const                extends SchemaProperty("const")
  final object Examples             extends SchemaProperty("examples")
  final object IfThenElse           extends SchemaProperty("ifThenElse")
  final object ReadOnly             extends SchemaProperty("readOnly")
  final object WriteOnly            extends SchemaProperty("writeOnly")
  final object Properties           extends SchemaProperty("properties")
  final object PatternProperties    extends SchemaProperty("patternProperties")
  final object AdditionalProperties extends SchemaProperty("additionalProperties")
  final object AdditionalItems      extends SchemaProperty("additionalItems")
  final object Items                extends SchemaProperty("items")
  final object PropertyNames        extends SchemaProperty("propertyNames")
  final object Contains             extends SchemaProperty("contains")
  final object AllOf                extends SchemaProperty("allOf")
  final object OneOf                extends SchemaProperty("oneOf")
  final object AnyOff               extends SchemaProperty("anyOf")
  final object Not                  extends SchemaProperty("not")
}
