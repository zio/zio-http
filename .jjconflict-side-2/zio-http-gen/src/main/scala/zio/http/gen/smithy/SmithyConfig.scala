package zio.http.gen.smithy

import zio.config.ConfigOps

import zio.http.gen.openapi.Config.NormalizeFields

/**
 * Configuration for Smithy code generation.
 *
 * @param fieldNamesNormalization
 *   Controls normalization of field names from Smithy conventions (e.g.,
 *   snake_case) to Scala conventions (camelCase). Original names are preserved
 *   via @fieldName annotations. Enabled by default.
 *
 * @param validateBeforeGeneration
 *   If true, validates the Smithy model before generating code. Validation
 *   errors will cause generation to fail with detailed messages.
 *
 * @param generateCompanionObjects
 *   If true, generates companion objects with Schema derivation for all case
 *   classes.
 *
 * @param unwrapSingleMemberInputs
 *   If true, operations with single-member input structures will use the member
 *   type directly instead of the wrapper. e.g.,
 *   `GetUserInput { userId: String }` -> endpoint takes `String`
 *
 * @param generateEndpointsObject
 *   If true, generates a single `Endpoints` object containing all endpoint
 *   definitions. If false, generates separate files per operation.
 *
 * @param endpointsObjectName
 *   Name of the generated endpoints object (default: "Endpoints").
 *
 * @param includeDocumentation
 *   If true, includes Smithy @documentation traits as Scaladoc comments in the
 *   generated code.
 *
 * @param typeMapping
 *   Custom mapping of Smithy shape names to Scala types. Allows overriding
 *   default type mappings for specific shapes. e.g., Map("UserId" ->
 *   "java.util.UUID")
 */
final case class SmithyConfig(
  fieldNamesNormalization: NormalizeFields,
  validateBeforeGeneration: Boolean,
  generateCompanionObjects: Boolean,
  unwrapSingleMemberInputs: Boolean,
  generateEndpointsObject: Boolean,
  endpointsObjectName: String,
  includeDocumentation: Boolean,
  typeMapping: Map[String, String],
)

object SmithyConfig {

  /**
   * Default configuration with field name normalization enabled.
   *
   *   - Field names are automatically converted to camelCase
   *   - Validation is enabled
   *   - Companion objects are generated
   *   - Documentation is included
   */
  val default: SmithyConfig = SmithyConfig(
    fieldNamesNormalization = NormalizeFields(
      enableAutomatic = true,
      manualOverrides = Map.empty,
    ),
    validateBeforeGeneration = true,
    generateCompanionObjects = true,
    unwrapSingleMemberInputs = false,
    generateEndpointsObject = true,
    endpointsObjectName = "Endpoints",
    includeDocumentation = true,
    typeMapping = Map.empty,
  )

  /**
   * Config with validation disabled for faster generation during development
   */
  val fast: SmithyConfig = default.copy(validateBeforeGeneration = false)

  /**
   * Config with field name normalization disabled (preserves original names)
   */
  val preserveFieldNames: SmithyConfig = default.copy(
    fieldNamesNormalization = NormalizeFields(
      enableAutomatic = false,
      manualOverrides = Map.empty,
    ),
  )

  /** ZIO Config descriptor for loading from configuration files */
  lazy val config: zio.Config[SmithyConfig] = (
    NormalizeFields.config.nested("fields-normalization") ++
      zio.Config.boolean("validate-before-generation").withDefault(default.validateBeforeGeneration) ++
      zio.Config.boolean("generate-companion-objects").withDefault(default.generateCompanionObjects) ++
      zio.Config.boolean("unwrap-single-member-inputs").withDefault(default.unwrapSingleMemberInputs) ++
      zio.Config.boolean("generate-endpoints-object").withDefault(default.generateEndpointsObject) ++
      zio.Config.string("endpoints-object-name").withDefault(default.endpointsObjectName) ++
      zio.Config.boolean("include-documentation").withDefault(default.includeDocumentation) ++
      zio.Config.table("type-mapping", zio.Config.string)
  ).to[SmithyConfig]
}
