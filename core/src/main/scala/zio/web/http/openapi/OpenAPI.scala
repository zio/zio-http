package zio.web.http.openapi

import java.net.URI

import zio.NonEmptyChunk
import zio.web.docs.Doc

object OpenAPI {

  /**
   * This is the root document object of the OpenAPI document.
   *
   * @param openapi This string MUST be the semantic version number of the OpenAPI Specification version that the OpenAPI document uses. The openapi field SHOULD be used by tooling specifications and clients to interpret the OpenAPI document. This is not related to the API info.version string.
   * @param info Provides metadata about the API. The metadata MAY be used by tooling as required.
   * @param servers A Seq of Server Objects, which provide connectivity information to a target server. If the servers property is empty, the default value would be a Server Object with a url value of /.
   * @param paths The available paths and operations for the API.
   * @param components An element to hold various schemas for the specification.
   * @param security A declaration of which security mechanisms can be used across the API. The list of values includes alternative security requirement objects that can be used. Only one of the security requirement objects need to be satisfied to authorize a request. Individual operations can override this definition. To make security optional, an empty security requirement ({}) can be included in the Seq.
   * @param tags A list of tags used by the specification with additional metadata. The order of the tags can be used to reflect on their order by the parsing tools. Not all tags that are used by the Operation Object must be declared. The tags that are not declared MAY be organized randomly or based on the tools’ logic. Each tag name in the list MUST be unique.
   * @param externalDocs Additional external documentation.
   */
  final case class OpenAPI(
    openapi: String,
    info: Info,
    servers: Seq[Server],
    paths: Paths,
    components: Option[Components],
    security: Seq[SecurityRequirement],
    tags: Seq[Tag],
    externalDocs: Doc
  )

  /**
   * The object provides metadata about the API. The metadata MAY be used by the clients if needed, and MAY be presented in editing or documentation generation tools for convenience.
   *
   * @param title The title of the API.
   * @param description A short description of the API.
   * @param termsOfService A URL to the Terms of Service for the API.
   * @param contact The contact information for the exposed API.
   * @param license The license information for the exposed API.
   * @param version The version of the OpenAPI document (which is distinct from the OpenAPI Specification version or the API implementation version).
   */
  final case class Info(
    title: String,
    description: Doc,
    termsOfService: URI,
    contact: Option[Contact],
    license: Option[License],
    version: String
  )

  /**
   * Contact information for the exposed API.
   *
   * @param name The identifying name of the contact person/organization.
   * @param url The URL pointing to the contact information.
   * @param email The email address of the contact person/organization. MUST be in the format of an email address. // TODO
   */
  final case class Contact(name: Option[String], url: Option[URI], email: String)

  /**
   * License information for the exposed API.
   *
   * @param name The license name used for the API.
   * @param url A URL to the license used for the API.
   */
  final case class License(name: String, url: Option[URI])

  /**
   * An object representing a Server.
   *
   * @param url A URL to the target host. This URL supports Server Variables and MAY be relative, to indicate that the host location is relative to the location where the OpenAPI document is being served. Variable substitutions will be made when a variable is named in {brackets}.
   * @param description Describing the host designated by the URL.
   * @param variables A map between a variable name and its value. The value is used for substitution in the server’s URL template.
   */
  final case class Server(url: URI, description: Doc, variables: Map[String, ServerVariable])

  /**
   * An object representing a Server Variable for server URL template substitution.
   *
   * @param enum An enumeration of string values to be used if the substitution options are from a limited set.
   * @param default The default value to use for substitution, which SHALL be sent if an alternate value is not supplied. Note this behavior is different than the Schema Object’s treatment of default values, because in those cases parameter values are optional. If the enum is defined, the value SHOULD exist in the enum’s values.
   * @param description A description for the server variable.
   */
  final case class ServerVariable(enum: NonEmptyChunk[String], default: String, description: Doc)

  /**
   * Holds a set of reusable objects for different aspects of the OAS. All objects defined within the components object will have no effect on the API unless they are explicitly referenced from properties outside the components object.
   *
   * @param schemas
   * @param responses
   * @param parameters
   * @param examples
   * @param requestBodies
   * @param headers
   * @param securitySchemes
   * @param links
   * @param callbacks
   */
  final case class Components(
    schemas: Map[Key, Schema],
    responses: Map[Key, Response],
    parameters: Map[Key, Parameter],
    examples: Map[Key, Example],
    requestBodies: Map[Key, RequestBody],
    headers: Map[Key, Header],
    securitySchemes: Map[Key, SecurityScheme],
    links: Map[Key, Link],
    callbacks: Map[Key, Callback]
  )

  final case class Key(name: String) {
    require("^[a-zA-Z0-9\\.\\-_]+$.".r.matches(name)) // TODO
  }

  /**
   * Holds the relative paths to the individual endpoints and their operations. The path is appended to the URL from the Server Object in order to construct the full URL. The Paths MAY be empty, due to ACL constraints.
   */
  type Paths = Map[Path, PathItem]

  /**
   * The field name of the relative path MUST begin with a forward slash (/). The path is appended (no relative URL resolution) to the expanded URL from the Server Object's url field in order to construct the full URL. Path templating is allowed. When matching URLs, concrete (non-templated) paths would be matched before their templated counterparts. Templated paths with the same hierarchy but different templated names MUST NOT exist as they are identical. In case of ambiguous matching, it’s up to the tooling to decide which one to use.
   *
   * @param name
   */
  final case class Path(name: String) {
    require("^/[a-zA-Z0-9\\.\\-_]+$.".r.matches(name)) // TODO
  }

  /**
   * Describes the operations available on a single path. A Path Item MAY be empty, due to ACL constraints. The path itself is still exposed to the documentation viewer but they will not know which operations and parameters are available.
   *
   * @param $ref Allows for an external definition of this path item. The referenced structure MUST be in the format of a Path Item Object. In case a Path Item Object field appears both in the defined object and the referenced object, the behavior is undefined.
   * @param summary An optional, string summary, intended to apply to all operations in this path.
   * @param description A description, intended to apply to all operations in this path.
   * @param get A definition of a GET operation on this path.
   * @param put A definition of a PUT operation on this path.
   * @param post A definition of a POST operation on this path.
   * @param delete A definition of a DELETE operation on this path.
   * @param options A definition of a OPTIONS operation on this path.
   * @param head A definition of a HEAD operation on this path.
   * @param patch A definition of a PATCH operation on this path.
   * @param trace A definition of a TRACE operation on this path.
   * @param servers An alternative server Seq to service all operations in this path.
   * @param parameters A list of parameters that are applicable for all the operations described under this path. These parameters can be overridden at the operation level, but cannot be removed there. The list MUST NOT include duplicated parameters. A unique parameter is defined by a combination of a name and location. The list can use the Reference Object to link to parameters that are defined at the OpenAPI Object’s components/parameters. // TODO
   */
  final case class PathItem(
    $ref: String,
    summary: String = "",
    description: Doc,
    get: Option[Operation],
    put: Option[Operation],
    post: Option[Operation],
    delete: Option[Operation],
    options: Option[Operation],
    head: Option[Operation],
    patch: Option[Operation],
    trace: Option[Operation],
    servers: Seq[Server],
    parameters: Set[Parameter]
  )

  /**
   * Describes a single API operation on a path.
   *
   * @param tags A list of tags for API documentation control. Tags can be used for logical grouping of operations by resources or any other qualifier.
   * @param summary A short summary of what the operation does.
   * @param description A verbose explanation of the operation behavior.
   * @param externalDocs Additional external documentation for this operation.
   * @param operationId Unique string used to identify the operation. The id MUST be unique among all operations described in the API. The operationId value is case-sensitive. Tools and libraries MAY use the operationId to uniquely identify an operation, therefore, it is RECOMMENDED to follow common programming naming conventions.
   * @param parameters A Seq of parameters that are applicable for this operation. If a parameter is already defined at the Path Item, the new definition will override it but can never remove it. The list MUST NOT include duplicated parameters. A unique parameter is defined by a combination of a name and location. The list can use the Reference Object to link to parameters that are defined at the OpenAPI Object’s components/parameters. // TODO
   * @param requestBody The request body applicable for this operation. The requestBody is only supported in HTTP methods where the HTTP 1.1 specification [RFC7231] has explicitly defined semantics for request bodies. In other cases where the HTTP spec is vague, requestBody SHALL be ignored by consumers.
   * @param responses The Seq of possible responses as they are returned from executing this operation.
   * @param callbacks A map of possible out-of band callbacks related to the parent operation. The key is a unique identifier for the Callback Object. Each value in the map is a Callback Object that describes a request that may be initiated by the API provider and the expected responses.
   * @param deprecated Declares this operation to be deprecated. Consumers SHOULD refrain from usage of the declared operation.
   * @param security A declaration of which security mechanisms can be used for this operation. The list of values includes alternative security requirement objects that can be used. Only one of the security requirement objects need to be satisfied to authorize a request. To make security optional, an empty security requirement ({}) can be included in the array. This definition overrides any declared top-level security. To remove a top-level security declaration, an empty array can be used. // TODO
   * @param servers An alternative server Seq to service this operation. If an alternative server object is specified at the Path Item Object or Root level, it will be overridden by this value.
   */
  final case class Operation(
    tags: Seq[String],
    summary: Option[String],
    description: Doc,
    externalDocs: Option[URI],
    operationId: Option[String],
    parameters: Set[Parameter],
    requestBody: Option[RequestBody],
    responses: Responses,
    callbacks: Map[String, Callback],
    deprecated: Boolean = false,
    security: Seq[SecurityRequirement],
    servers: Seq[Server]
  )

  /**
   * Describes a single operation parameter.
   *
   * @param name The name of the parameter. Parameter names are case sensitive.
   * @param in The location of the parameter.
   * @param description A brief description of the parameter.
   * @param required Determines whether this parameter is mandatory. If the parameter location is "path", this property MUST be true. Otherwise, the property MAY be false. // TODO
   * @param deprecated Specifies that a parameter is deprecated and SHOULD be transitioned out of usage.
   * @param allowEmptyValue Sets the ability to pass empty-valued parameters. This is valid only for query parameters and allows sending a parameter with an empty value. If style is used, and if behavior is n/a (cannot be serialized), the value of allowEmptyValue SHALL be ignored. Use of this property is NOT RECOMMENDED, as it is likely to be removed in a later revision.
   */
  // TODO: Parameter for each in
  final case class Parameter(
    name: String,
    in: Parameter.In,
    description: Doc,
    required: Boolean,
    deprecated: Boolean = false,
    allowEmptyValue: Boolean = false,
    // TODO: add to ScalaDoc
    style: Option[String] = None,          // TODO: default values based on in
    explode: Option[Boolean] = None,       // TODO: default value
    allowReserved: Option[Boolean] = None, // TODO: only with in==query with default false
    schema: Option[Schema] = None,
    examples: Map[String, Example] = Map.empty
  ) {
    // TODO: maybe add content field (https://spec.openapis.org/oas/v3.0.3#parameter-object)

    /**
     * A unique parameter is defined by a combination of a name and location.
     */
    override def equals(obj: Any): Boolean = obj match {
      case p: Parameter if name == p.name && in == p.in => true
      case _                                            => false
    }
  }

  object Parameter {
    sealed trait In

    object In {

      /** Parameters that are appended to the URL. For example, in /items?id=###, the query parameter is id. */
      case object Query extends  In

      /** Custom headers that are expected as part of the request. Note that [RFC7230] states header names are case insensitive. */
      case object Header extends In

      /** Used together with Path Templating, where the parameter value is actually part of the operation’s URL. This does not include the host or base path of the API. For example, in /items/{itemId}, the path parameter is itemId. */
      case object Path extends In

      /** Used to pass a specific cookie value to the API. */
      case object Cookie extends In
    }
  }

  /**
   * Describes a single request body.
   *
   * @param description A brief description of the request body. This could contain examples of use.
   * @param content The content of the request body. The key is a media type or [media type range]appendix-D) and the value describes it. For requests that match multiple keys, only the most specific key is applicable.
   * @param required Determines if the request body is required in the request.
   */
  // TODO: SpecificationExtensions (https://spec.openapis.org/oas/v3.0.3#specificationExtensions)
  final case class RequestBody(description: Doc, content: Map[String, MediaType], required: Boolean = false)

  /**
   * Each Media Type Object provides schema and examples for the media type identified by its key.
   *
   * @param schema The schema defining the content of the request, response, or parameter.
   * @param examples Examples of the media type. Each example object SHOULD match the media type and specified schema if present. If referencing a schema which contains an example, the examples value SHALL override the example provided by the schema.
   * @param encoding A map between a property name and its encoding information. The key, being the property name, MUST exist in the schema as a property. The encoding object SHALL only apply to requestBody objects when the media type is multipart or application/x-www-form-urlencoded.
   */
  final case class MediaType(schema: Schema, examples: Map[String, Example], encoding: Map[String, Encoding])

  /**
   * A single encoding definition applied to a single schema property.
   *
   * @param contentType The Content-Type for encoding a specific property. // TODO: default value (https://spec.openapis.org/oas/v3.0.3#encoding-object)
   * @param headers A map allowing additional information to be provided as headers, for example Content-Disposition. Content-Type is described separately and SHALL be ignored in this section. This property SHALL be ignored if the request body media type is not a multipart.
   * @param style Describes how a specific property value will be serialized depending on its type. This property SHALL be ignored if the request body media type is not application/x-www-form-urlencoded. // TODO: values
   * @param explode When this is true, property values of type array or object generate separate parameters for each value of the array, or key-value-pair of the map. // TODO: default values
   * @param allowReserved Determines whether the parameter value SHOULD allow reserved characters, as defined by [RFC3986] to be included without percent-encoding. This property SHALL be ignored if the request body media type is not application/x-www-form-urlencoded.
   */
  final case class Encoding(
    contentType: String,
    headers: Map[String, Header],
    style: String,
    explode: Boolean,
    allowReserved: Boolean = false
  )

  /**
   * A container for the expected responses of an operation. The container maps a HTTP response code to the expected response.
   * The Responses Object MUST contain at least one response code, and it SHOULD be the response for a successful operation call. // TODO
   */
  type Responses = Map[Int, Response] // TODO: must include default // TODO: use HttpStatusCode instead of Int

  /**
   * Describes a single response from an API Operation, including design-time, static links to operations based on the response.
   *
   * @param description A short description of the response.
   * @param headers Maps a header name to its definition. [RFC7230] states header names are case insensitive. If a response header is defined with the name "Content-Type", it SHALL be ignored.
   * @param content A map containing descriptions of potential response payloads. The key is a media type or [media type range]appendix-D) and the value describes it. For responses that match multiple keys, only the most specific key is applicable.
   * @param links A map of operations links that can be followed from the response. The key of the map is a short name for the link, following the naming constraints of the names for Component Objects.
   */
  final case class Response(
    description: Doc,
    headers: Map[String, Header],
    content: Map[String, MediaType],
    links: Map[String, Link]
  )

  /**
   * A map of possible out-of band callbacks related to the parent operation. Each value in the map is a Path Item Object that describes a set of requests that may be initiated by the API provider and the expected responses. The key value used to identify the path item object is an expression, evaluated at runtime, that identifies a URL to use for the callback operation.
   *
   * @param expressions A Path Item Object used to define a callback request and expected responses. // TODO: maybe not String?
   */
  final case class Callback(expressions: Map[String, PathItem])
  // TODO: specification extensions

  /**
   * In all cases, the example value is expected to be compatible with the type schema of its associated value. Tooling implementations MAY choose to validate compatibility automatically, and reject the example value(s) if incompatible.
   *
   * @param summary Short description for the example.
   * @param description Long description for the example.
   * @param externalValue A URL that points to the literal example. This provides the capability to reference examples that cannot easily be included in JSON or YAML documents.
   */
  final case class Example(summary: String, description: Doc, externalValue: URI)
  // TODO: specification extensions

  /**
   * The Link object represents a possible design-time link for a response. The presence of a link does not guarantee the caller’s ability to successfully invoke it, rather it provides a known relationship and traversal mechanism between responses and other operations.
   *
   * Unlike dynamic links (i.e. links provided in the response payload), the OAS linking mechanism does not require link information in the runtime response.
   *
   * For computing links, and providing instructions to execute them, a runtime expression is used for accessing values in an operation and using them as parameters while invoking the linked operation.
   *
   * @param operationRef A relative or absolute URI reference to an OAS operation. This field is mutually exclusive of the operationId field, and MUST point to an Operation Object. Relative operationRef values MAY be used to locate an existing Operation Object in the OpenAPI definition.
   * @param operationId The name of an existing, resolvable OAS operation, as defined with a unique operationId. This field is mutually exclusive of the operationRef field.
   * @param parameters A map representing parameters to pass to an operation as specified with operationId or identified via operationRef. The key is the parameter name to be used, whereas the value can be a constant or an expression to be evaluated and passed to the linked operation. The parameter name can be qualified using the parameter location [{in}.]{name} for operations that use the same parameter name in different locations (e.g. path.id).
   * @param requestBody A literal value or {expression} to use as a request body when calling the target operation.
   * @param description A description of the link.
   * @param server A server object to be used by the target operation.
   */
  final case class Link(
    operationRef: Option[URI],
    operationId: Option[String],
    parameters: Map[String, Any],
    requestBody: Any,
    description: Doc,
    server: Option[Server]
  ) {

    /**
     * A linked operation MUST be identified using either an operationRef or operationId. In the case of an operationId, it MUST be unique and resolved in the scope of the OAS document. Because of the potential for name clashes, the operationRef syntax is preferred for specifications with external references.
     */
    require((operationRef.isDefined && operationId.isEmpty) || (operationRef.isEmpty && operationRef.isDefined))
  }
  // TODO: specification extensions

  final case class Header() // TODO: special kind of Parameter (https://spec.openapis.org/oas/v3.0.3#header-object)

  /**
   * Adds metadata to a single tag that is used by the Operation Object. It is not mandatory to have a Tag Object per tag defined in the Operation Object instances.
   *
   * @param name The name of the tag.
   * @param description A short description for the tag.
   * @param externalDocs Additional external documentation for this tag.
   */
  final case class Tag(name: String, description: Doc, externalDocs: URI)

  /**
   * A simple object to allow referencing other components in the specification, internally and externally.
   *
   * @param $ref The reference string.
   */
  final case class Reference($ref: String)
  // TODO: extend others with this

  /**
   * The Schema Object allows the definition of input and output data types.
   *
   * @param nullable A true value adds "null" to the allowed type specified by the type keyword, only if type is explicitly defined within the same Schema Object. Other Schema Object constraints retain their defined behavior, and therefore may disallow the use of null as a value. A false value leaves the specified or default type unmodified.
   * @param discriminator Adds support for polymorphism. The discriminator is an object name that is used to differentiate between other schemas which may satisfy the payload description.
   * @param readOnly Relevant only for Schema "properties" definitions. Declares the property as “read only”. This means that it MAY be sent as part of a response but SHOULD NOT be sent as part of the request. If the property is marked as readOnly being true and is in the required list, the required will take effect on the response only.
   * @param writeOnly Relevant only for Schema "properties" definitions. Declares the property as “write only”. Therefore, it MAY be sent as part of a request but SHOULD NOT be sent as part of the response. If the property is marked as writeOnly being true and is in the required list, the required will take effect on the request only.
   * @param xml This MAY be used only on properties schemas. It has no effect on root schemas. Adds additional metadata to describe the XML representation of this property.
   * @param externalDocs Additional external documentation for this schema.
   * @param example A free-form property to include an example of an instance for this schema.
   * @param deprecated Specifies that a schema is deprecated and SHOULD be transitioned out of usage.
   */
  final case class Schema(
    nullable: Boolean = false,
    discriminator: Option[Discriminator],
    readOnly: Boolean = false,
    writeOnly: Boolean = false,
    xml: Option[XML],
    externalDocs: URI,
    example: String,
    deprecated: Boolean = false
  ) {

    /**
     * A property MUST NOT be marked as both readOnly and writeOnly being true.
     */
    require((readOnly && !writeOnly) || (!readOnly && writeOnly) || (!readOnly && !writeOnly))
  }
  // TODO: specification extensions

  /**
   * When request bodies or response payloads may be one of a number of different schemas, a discriminator object can be used to aid in serialization, deserialization, and validation. The discriminator is a specific object in a schema which is used to inform the consumer of the specification of an alternative schema based on the value associated with it.
   *
   * When using the discriminator, inline schemas will not be considered.
   *
   * @param propertyName The name of the property in the payload that will hold the discriminator value.
   * @param mapping An object to hold mappings between payload values and schema names or references.
   */
  final case class Discriminator(propertyName: String, mapping: Map[String, String])

  /**
   * A metadata object that allows for more fine-tuned XML model definitions.
   *
   * When using arrays, XML element names are not inferred (for singular/plural forms) and the name property SHOULD be used to add that information.
   *
   * @param name Replaces the name of the element/attribute used for the described schema property. When defined within items, it will affect the name of the individual XML elements within the list. When defined alongside type being array (outside the items), it will affect the wrapping element and only if wrapped is true. If wrapped is false, it will be ignored.
   * @param namespace The URI of the namespace definition.
   * @param prefix The prefix to be used for the name.
   * @param attribute Declares whether the property definition translates to an attribute instead of an element.
   * @param wrapped MAY be used only for an array definition. Signifies whether the array is wrapped (for example, <books><book/><book/></books>) or unwrapped (<book/><book/>). The definition takes effect only when defined alongside type being array (outside the items).
   */
  final case class XML(name: String, namespace: URI, prefix: String, attribute: Boolean = false, wrapped: Boolean)

  // TODO: versions per applies to (https://spec.openapis.org/oas/v3.0.3#security-scheme-object)
  final case class SecurityScheme(`type`: SecurityScheme.Type, description: Doc)
  // TODO: specification extensions

  object SecurityScheme {
    sealed trait Type

    object Type {
      case object ApiKey extends Type
      case object Http extends Type
      case object OAuth2 extends Type
      case object OpenIdConnect extends Type
    }
  }

  /**
   * Lists the required security schemes to execute this operation. The name used for each property MUST correspond to a security scheme declared in the Security Schemes under the Components Object.
   *
   * Security Requirement Objects that contain multiple schemes require that all schemes MUST be satisfied for a request to be authorized. This enables support for scenarios where multiple query parameters or HTTP headers are required to convey security information.
   *
   * When a list of Security Requirement Objects is defined on the OpenAPI Object or Operation Object, only one of the Security Requirement Objects in the list needs to be satisfied to authorize the request.
   *
   * @param securitySchemes If the security scheme is of type "oauth2" or "openIdConnect", then the value is a list of scope names required for the execution, and the list MAY be empty if authorization does not require a specified scope. For other security scheme types, the array MUST be empty. // TODO
   */
  final case class SecurityRequirement(securitySchemes: Map[SecurityScheme.Type, Seq[String]])
}
