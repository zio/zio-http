package zio.http.doc.asyncapi.model

sealed abstract case class SchemaProperty(value: String) {
  override def toString: String = value
}

object SchemaProperty {
  final object TITLE                 extends SchemaProperty("title")
  final object TYPE                  extends SchemaProperty("type")
  final object REQUIRED              extends SchemaProperty("required")
  final object MULTIPLEOF            extends SchemaProperty("multipleOf")
  final object MAXIMUM               extends SchemaProperty("maximum")
  final object EXCLUSIVE_MAXIMUM     extends SchemaProperty("exclusiveMaximum")
  final object MINIMUM               extends SchemaProperty("minimum")
  final object EXCLUSIVE_MINIMUM     extends SchemaProperty("exclusiveMinimum")
  final object MAX_LENGTH            extends SchemaProperty("maxLength")
  final object MIN_LENGTH            extends SchemaProperty("minLength")
  final object PATTERN               extends SchemaProperty("pattern")
  final object MAX_ITEMS             extends SchemaProperty("maxItems")
  final object MIN_ITEMS             extends SchemaProperty("minItems")
  final object UNIQUE_ITEMS          extends SchemaProperty("uniqueItems")
  final object MAX_PROPERTIES        extends SchemaProperty("maxProperties")
  final object MIN_PROPERTIES        extends SchemaProperty("minProperties")
  final object ENUM                  extends SchemaProperty("enum")
  final object CONST                 extends SchemaProperty("const")
  final object EXAMPLES              extends SchemaProperty("examples")
  final object IF_THEN_ELSE          extends SchemaProperty("ifThenElse")
  final object READ_ONLY             extends SchemaProperty("readOnly")
  final object WRITE_ONLY            extends SchemaProperty("writeOnly")
  final object PROPERTIES            extends SchemaProperty("properties")
  final object PATTERN_PROPERTIES    extends SchemaProperty("patternProperties")
  final object ADDITIONAL_PROPERTIES extends SchemaProperty("additionalProperties")
  final object ADDITIONAL_ITEMS      extends SchemaProperty("additionalItems")
  final object ITEMS                 extends SchemaProperty("items")
  final object PROPERTY_NAMES        extends SchemaProperty("propertyNames")
  final object CONTAINS              extends SchemaProperty("contains")
  final object ALL_OF                extends SchemaProperty("allOf")
  final object ONE_OF                extends SchemaProperty("oneOf")
  final object ANY_OF                extends SchemaProperty("anyOf")
  final object NOT                   extends SchemaProperty("not")
}
