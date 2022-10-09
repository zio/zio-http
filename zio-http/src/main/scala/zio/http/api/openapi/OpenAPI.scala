/**
 * Credit for the initial work goes to @saeltz (Bendix Saltz) and
 * @zagyi
 *   (Balazs Zagyvai), who did this work in `zio-microservice` under the ZIO CLA
 *   and Apache 2 license.
 */
package zio.http.api.openapi

import zio.NonEmptyChunk
import zio.http.api.openapi.JsonRenderer._
import zio.http.api.{Doc, openapi}
import zio.http.model.Status

import java.net.URI
import scala.util.matching.Regex

private[openapi] sealed trait OpenAPIBase {
  self =>
  def toJson: String
}

object OpenAPI {

  /**
   * This is the root document object of the OpenAPI document.
   *
   * @param openapi
   *   This string MUST be the semantic version number of the OpenAPI
   *   Specification version that the OpenAPI document uses. The openapi field
   *   SHOULD be used by tooling specifications and clients to interpret the
   *   OpenAPI document. This is not related to the API info.version string.
   * @param info
   *   Provides metadata about the API. The metadata MAY be used by tooling as
   *   required.
   * @param servers
   *   A List of Server Objects, which provide connectivity information to a
   *   target server. If the servers property is empty, the default value would
   *   be a Server Object with a url value of /.
   * @param paths
   *   The available paths and operations for the API.
   * @param components
   *   An element to hold various schemas for the specification.
   * @param security
   *   A declaration of which security mechanisms can be used across the API.
   *   The list of values includes alternative security requirement objects that
   *   can be used. Only one of the security requirement objects need to be
   *   satisfied to authorize a request. Individual operations can override this
   *   definition. To make security optional, an empty security requirement ({})
   *   can be included in the List.
   * @param tags
   *   A list of tags used by the specification with additional metadata. The
   *   order of the tags can be used to reflect on their order by the parsing
   *   tools. Not all tags that are used by the Operation Object must be
   *   declared. The tags that are not declared MAY be organized randomly or
   *   based on the tools’ logic. Each tag name in the list MUST be unique.
   * @param externalDocs
   *   Additional external documentation.
   */
  final case class OpenAPI(
    openapi: String,
    info: Info,
    servers: List[Server],
    paths: Paths,
    components: Option[Components],
    security: List[SecurityRequirement],
    tags: List[Tag],
    externalDocs: Option[ExternalDoc],
  ) extends OpenAPIBase {
    def toJson: String =
      JsonRenderer.renderFields(
        "openapi"      -> openapi,
        "info"         -> info,
        "servers"      -> servers,
        "paths"        -> paths,
        "components"   -> components,
        "security"     -> security,
        "tags"         -> tags,
        "externalDocs" -> externalDocs,
      )
  }

  /**
   * Allows referencing an external resource for extended documentation.
   *
   * @param description
   *   A short description of the target documentation. CommonMark syntax MAY be
   *   used for rich text representation.
   * @param url
   *   The URL for the target documentation.
   */
  final case class ExternalDoc(description: Option[Doc], url: URI) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields("description" -> description, "url" -> url)
  }

  /**
   * The object provides metadata about the API. The metadata MAY be used by the
   * clients if needed, and MAY be presented in editing or documentation
   * generation tools for convenience.
   *
   * @param title
   *   The title of the API.
   * @param description
   *   A short description of the API.
   * @param termsOfService
   *   A URL to the Terms of Service for the API.
   * @param contact
   *   The contact information for the exposed API.
   * @param license
   *   The license information for the exposed API.
   * @param version
   *   The version of the OpenAPI document (which is distinct from the OpenAPI
   *   Specification version or the API implementation version).
   */
  final case class Info(
    title: String,
    description: Doc,
    termsOfService: URI,
    contact: Option[Contact],
    license: Option[License],
    version: String,
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "title"          -> title,
      "description"    -> description,
      "termsOfService" -> termsOfService,
      "contact"        -> contact,
      "license"        -> license,
      "version"        -> version,
    )
  }

  /**
   * Contact information for the exposed API.
   *
   * @param name
   *   The identifying name of the contact person/organization.
   * @param url
   *   The URL pointing to the contact information.
   * @param email
   *   The email address of the contact person/organization. MUST be in the
   *   format of an email address.
   */
  final case class Contact(name: Option[String], url: Option[URI], email: String) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields("name" -> name, "url" -> url, "email" -> email)
  }

  /**
   * License information for the exposed API.
   *
   * @param name
   *   The license name used for the API.
   * @param url
   *   A URL to the license used for the API.
   */
  final case class License(name: String, url: Option[URI]) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields("name" -> name, "url" -> url)
  }

  /**
   * An object representing a Server.
   *
   * @param url
   *   A URL to the target host. This URL supports Server Variables and MAY be
   *   relative, to indicate that the host location is relative to the location
   *   where the OpenAPI document is being served. Variable substitutions will
   *   be made when a variable is named in {brackets}.
   * @param description
   *   Describing the host designated by the URL.
   * @param variables
   *   A map between a variable name and its value. The value is used for
   *   substitution in the server’s URL template.
   */
  final case class Server(url: URI, description: Doc, variables: Map[String, ServerVariable])
      extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "url"         -> url,
      "description" -> description,
      "variables"   -> variables,
    )
  }

  /**
   * An object representing a Server Variable for server URL template
   * substitution.
   *
   * @param enum
   *   An enumeration of string values to be used if the substitution options
   *   are from a limited set.
   * @param default
   *   The default value to use for substitution, which SHALL be sent if an
   *   alternate value is not supplied. Note this behavior is different than the
   *   Schema Object’s treatment of default values, because in those cases
   *   parameter values are optional. If the enum is defined, the value SHOULD
   *   exist in the enum’s values.
   * @param description
   *   A description for the server variable.
   */
  final case class ServerVariable(`enum`: NonEmptyChunk[String], default: String, description: Doc)
      extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "enum"        -> `enum`,
      "default"     -> default,
      "description" -> description,
    )
  }

  /**
   * Holds a set of reusable objects for different aspects of the OAS. All
   * objects defined within the components object will have no effect on the API
   * unless they are explicitly referenced from properties outside the
   * components object.
   *
   * @param schemas
   *   An object to hold reusable Schema Objects.
   * @param responses
   *   An object to hold reusable Response Objects.
   * @param parameters
   *   An object to hold reusable Parameter Objects.
   * @param examples
   *   An object to hold reusable Example Objects.
   * @param requestBodies
   *   An object to hold reusable Request Body Objects.
   * @param headers
   *   An object to hold reusable Header Objects.
   * @param securitySchemes
   *   An object to hold reusable Security Scheme Objects.
   * @param links
   *   An object to hold reusable Link Objects.
   * @param callbacks
   *   An object to hold reusable Callback Objects.
   */
  final case class Components(
    schemas: Map[Key, SchemaOrReference],
    responses: Map[Key, ResponseOrReference],
    parameters: Map[Key, ParameterOrReference],
    examples: Map[Key, ExampleOrReference],
    requestBodies: Map[Key, RequestBodyOrReference],
    headers: Map[Key, HeaderOrReference],
    securitySchemes: Map[Key, SecuritySchemeOrReference],
    links: Map[Key, LinkOrReference],
    callbacks: Map[Key, CallbackOrReference],
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "schemas"         -> schemas,
      "responses"       -> responses,
      "parameters"      -> parameters,
      "examples"        -> examples,
      "requestBodies"   -> requestBodies,
      "headers"         -> headers,
      "securitySchemes" -> securitySchemes,
      "links"           -> links,
      "callbacks"       -> callbacks,
    )
  }

  sealed abstract case class Key private (name: String) extends openapi.OpenAPIBase {
    override def toJson: String = name
  }

  object Key {

    /**
     * All Components objects MUST use Keys that match the regular expression.
     */
    val validName: Regex = "^[a-zA-Z0-9.\\-_]+$.".r

    def fromString(name: String): Option[Key] = name match {
      case validName() => Some(new Key(name) {})
      case _           => None
    }
  }

  /**
   * Holds the relative paths to the individual endpoints and their operations.
   * The path is appended to the URL from the Server Object in order to
   * construct the full URL. The Paths MAY be empty, due to ACL constraints.
   */
  type Paths = Map[Path, PathItem]

  /**
   * The path is appended (no relative URL resolution) to the expanded URL from
   * the Server Object's url field in order to construct the full URL. Path
   * templating is allowed. When matching URLs, concrete (non-templated) paths
   * would be matched before their templated counterparts. Templated paths with
   * the same hierarchy but different templated names MUST NOT exist as they are
   * identical. In case of ambiguous matching, it’s up to the tooling to decide
   * which one to use.
   *
   * @param name
   *   The field name of the relative path MUST begin with a forward slash (/).
   */
  sealed abstract case class Path private (name: String) extends openapi.OpenAPIBase {
    override def toJson: String = name
  }

  object Path {
    // todo maybe not the best regex, but the old one was not working at all
    val validPath: Regex = "/[a-zA-Z0-9\\-_\\{\\}]+".r

    def fromString(name: String): Option[Path] = name match {
      case validPath() => Some(new Path(name) {})
      case _           => None
    }
  }

  /**
   * Describes the operations available on a single path. A Path Item MAY be
   * empty, due to ACL constraints. The path itself is still exposed to the
   * documentation viewer but they will not know which operations and parameters
   * are available.
   *
   * @param ref
   *   Allows for an external definition of this path item. The referenced
   *   structure MUST be in the format of a Path Item Object. In case a Path
   *   Item Object field appears both in the defined object and the referenced
   *   object, the behavior is undefined.
   * @param summary
   *   An optional, string summary, intended to apply to all operations in this
   *   path.
   * @param description
   *   A description, intended to apply to all operations in this path.
   * @param get
   *   A definition of a GET operation on this path.
   * @param put
   *   A definition of a PUT operation on this path.
   * @param post
   *   A definition of a POST operation on this path.
   * @param delete
   *   A definition of a DELETE operation on this path.
   * @param options
   *   A definition of a OPTIONS operation on this path.
   * @param head
   *   A definition of a HEAD operation on this path.
   * @param patch
   *   A definition of a PATCH operation on this path.
   * @param trace
   *   A definition of a TRACE operation on this path.
   * @param servers
   *   An alternative server List to service all operations in this path.
   * @param parameters
   *   A Set of parameters that are applicable for all the operations described
   *   under this path. These parameters can be overridden at the operation
   *   level, but cannot be removed there. The Set can use the Reference Object
   *   to link to parameters that are defined at the OpenAPI Object’s
   *   components/parameters.
   */
  final case class PathItem(
    ref: String,
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
    servers: List[Server],
    parameters: Set[ParameterOrReference],
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      s"$$ref"      -> ref,
      "summary"     -> summary,
      "description" -> description,
      "get"         -> get,
      "put"         -> put,
      "post"        -> post,
      "delete"      -> delete,
      "options"     -> options,
      "head"        -> head,
      "patch"       -> patch,
      "trace"       -> trace,
      "servers"     -> servers,
      "parameters"  -> parameters,
    )
  }

  /**
   * Describes a single API operation on a path.
   *
   * @param tags
   *   A list of tags for API documentation control. Tags can be used for
   *   logical grouping of operations by resources or any other qualifier.
   * @param summary
   *   A short summary of what the operation does.
   * @param description
   *   A verbose explanation of the operation behavior.
   * @param externalDocs
   *   Additional external documentation for this operation.
   * @param operationId
   *   Unique string used to identify the operation. The id MUST be unique among
   *   all operations described in the API. The operationId value is
   *   case-sensitive. Tools and libraries MAY use the operationId to uniquely
   *   identify an operation, therefore, it is RECOMMENDED to follow common
   *   programming naming conventions.
   * @param parameters
   *   A List of parameters that are applicable for this operation. If a
   *   parameter is already defined at the Path Item, the new definition will
   *   override it but can never remove it. The list MUST NOT include duplicated
   *   parameters. A unique parameter is defined by a combination of a name and
   *   location. The list can use the Reference Object to link to parameters
   *   that are defined at the OpenAPI Object’s components/parameters.
   * @param requestBody
   *   The request body applicable for this operation. The requestBody is only
   *   supported in HTTP methods where the HTTP 1.1 specification [RFC7231] has
   *   explicitly defined semantics for request bodies. In other cases where the
   *   HTTP spec is vague, requestBody SHALL be ignored by consumers.
   * @param responses
   *   The List of possible responses as they are returned from executing this
   *   operation.
   * @param callbacks
   *   A map of possible out-of band callbacks related to the parent operation.
   *   The key is a unique identifier for the Callback Object. Each value in the
   *   map is a Callback Object that describes a request that may be initiated
   *   by the API provider and the expected responses.
   * @param deprecated
   *   Declares this operation to be deprecated. Consumers SHOULD refrain from
   *   usage of the declared operation.
   * @param security
   *   A declaration of which security mechanisms can be used for this
   *   operation. The List of values includes alternative security requirement
   *   objects that can be used. Only one of the security requirement objects
   *   need to be satisfied to authorize a request. To make security optional,
   *   an empty security requirement ({}) can be included in the array. This
   *   definition overrides any declared top-level security. To remove a
   *   top-level security declaration, an empty List can be used.
   * @param servers
   *   An alternative server List to service this operation. If an alternative
   *   server object is specified at the Path Item Object or Root level, it will
   *   be overridden by this value.
   */
  final case class Operation(
    tags: List[String],
    summary: String = "",
    description: Doc,
    externalDocs: Option[ExternalDoc],
    operationId: Option[String],
    parameters: Set[ParameterOrReference],
    requestBody: Option[RequestBodyOrReference],
    responses: Responses,
    callbacks: Map[String, CallbackOrReference],
    deprecated: Boolean = false,
    security: List[SecurityRequirement],
    servers: List[Server],
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "tags"         -> tags,
      "summary"      -> summary,
      "description"  -> description,
      "externalDocs" -> externalDocs,
      "operationId"  -> operationId,
      "parameters"   -> parameters,
      "requestBody"  -> requestBody,
      "responses"    -> responses,
      "callbacks"    -> callbacks,
      "deprecated"   -> deprecated,
      "security"     -> security,
      "servers"      -> servers,
    )
  }

  sealed trait ParameterOrReference extends openapi.OpenAPIBase

  /**
   * Describes a single operation parameter.
   */
  sealed trait Parameter extends ParameterOrReference {
    def name: String
    def in: String
    def description: Doc
    def required: Boolean
    def deprecated: Boolean
    def allowEmptyValue: Boolean
    def definition: Parameter.Definition
    def explode: Boolean
    def examples: Map[String, ExampleOrReference]

    /**
     * A unique parameter is defined by a combination of a name and location.
     */
    override def equals(obj: Any): Boolean = obj match {
      case p: Parameter.QueryParameter if name == p.name && in == p.in => true
      case _                                                           => false
    }

    override def toJson: String =
      JsonRenderer.renderFields(
        "name"            -> name,
        "in"              -> in,
        "description"     -> description,
        "required"        -> required,
        "deprecated"      -> deprecated,
        "allowEmptyValue" -> allowEmptyValue,
        "definition"      -> definition,
        "explode"         -> explode,
        "examples"        -> examples,
      )
  }

  object Parameter {
    sealed trait Definition extends SchemaOrReference

    object Definition {
      final case class Content(key: String, mediaType: String) extends Definition {
        override def toJson: String = JsonRenderer.renderFields(
          "key"       -> key,
          "mediaType" -> mediaType,
        )
      }
    }

    sealed trait PathStyle

    sealed trait QueryStyle

    object QueryStyle {
      case object Matrix extends PathStyle

      case object Label extends PathStyle

      case object Simple extends PathStyle

      case object Form extends QueryStyle

      case object SpaceDelimited extends QueryStyle

      case object PipeDelimited extends QueryStyle

      case object DeepObject extends QueryStyle
    }

    /**
     * Parameters that are appended to the URL. For example, in /items?id=###,
     * the query parameter is id.
     *
     * @param name
     *   The name of the parameter. Parameter names are case sensitive.
     * @param description
     *   A brief description of the parameter.
     * @param deprecated
     *   Specifies that a parameter is deprecated and SHOULD be transitioned out
     *   of usage.
     * @param allowEmptyValue
     *   Sets the ability to pass empty-valued parameters. This is valid only
     *   for query parameters and allows sending a parameter with an empty
     *   value. If style is used, and if behavior is n/a (cannot be serialized),
     *   the value of allowEmptyValue SHALL be ignored. Use of this property is
     *   NOT RECOMMENDED, as it is likely to be removed in a later revision.
     */
    final case class QueryParameter(
      name: String,
      description: Doc,
      deprecated: Boolean = false,
      allowEmptyValue: Boolean = false,
      definition: Definition,
      allowReserved: Boolean = false,
      style: QueryStyle = QueryStyle.Form,
      explode: Boolean = true,
      examples: Map[String, ExampleOrReference],
    ) extends Parameter {
      def in: String        = "query"
      def required: Boolean = true
    }

    /**
     * Custom headers that are expected as part of the request. Note that
     * [RFC7230] states header names are case insensitive.
     *
     * @param name
     *   The name of the parameter. Parameter names are case sensitive.
     * @param description
     *   A brief description of the parameter.
     * @param required
     *   Determines whether this parameter is mandatory.
     * @param deprecated
     *   Specifies that a parameter is deprecated and SHOULD be transitioned out
     *   of usage.
     * @param allowEmptyValue
     *   Sets the ability to pass empty-valued parameters. This is valid only
     *   for query parameters and allows sending a parameter with an empty
     *   value. If style is used, and if behavior is n/a (cannot be serialized),
     *   the value of allowEmptyValue SHALL be ignored. Use of this property is
     *   NOT RECOMMENDED, as it is likely to be removed in a later revision.
     */
    final case class HeaderParameter(
      name: String,
      description: Doc,
      required: Boolean,
      deprecated: Boolean = false,
      allowEmptyValue: Boolean = false,
      definition: Definition,
      explode: Boolean = false,
      examples: Map[String, ExampleOrReference],
    ) extends Parameter {
      def in: String    = "header"
      def style: String = "simple"
    }

    /**
     * Used together with Path Templating, where the parameter value is actually
     * part of the operation’s URL. This does not include the host or base path
     * of the API. For example, in /items/{itemId}, the path parameter is
     * itemId.
     *
     * @param name
     *   The name of the parameter. Parameter names are case sensitive.
     * @param description
     *   A brief description of the parameter.
     * @param required
     *   Determines whether this parameter is mandatory.
     * @param deprecated
     *   Specifies that a parameter is deprecated and SHOULD be transitioned out
     *   of usage.
     * @param allowEmptyValue
     *   Sets the ability to pass empty-valued parameters. This is valid only
     *   for query parameters and allows sending a parameter with an empty
     *   value. If style is used, and if behavior is n/a (cannot be serialized),
     *   the value of allowEmptyValue SHALL be ignored. Use of this property is
     *   NOT RECOMMENDED, as it is likely to be removed in a later revision.
     */
    final case class PathParameter(
      name: String,
      description: Doc,
      required: Boolean,
      deprecated: Boolean = false,
      allowEmptyValue: Boolean = false,
      definition: Definition,
      style: PathStyle = QueryStyle.Simple,
      explode: Boolean = false,
      examples: Map[String, ExampleOrReference],
    ) extends Parameter {
      def in: String = "path"
    }

    /**
     * Used to pass a specific cookie value to the API.
     *
     * @param name
     *   The name of the parameter. Parameter names are case sensitive.
     * @param description
     *   A brief description of the parameter.
     * @param required
     *   Determines whether this parameter is mandatory.
     * @param deprecated
     *   Specifies that a parameter is deprecated and SHOULD be transitioned out
     *   of usage.
     * @param allowEmptyValue
     *   Sets the ability to pass empty-valued parameters. This is valid only
     *   for query parameters and allows sending a parameter with an empty
     *   value. If style is used, and if behavior is n/a (cannot be serialized),
     *   the value of allowEmptyValue SHALL be ignored. Use of this property is
     *   NOT RECOMMENDED, as it is likely to be removed in a later revision.
     */
    final case class CookieParameter(
      name: String,
      description: Doc,
      required: Boolean,
      deprecated: Boolean = false,
      allowEmptyValue: Boolean = false,
      definition: Definition,
      explode: Boolean = false,
      examples: Map[String, ExampleOrReference],
    ) extends Parameter {
      def in: String    = "cookie"
      def style: String = "form"
    }
  }

  sealed trait HeaderOrReference extends openapi.OpenAPIBase

  final case class Header(
    description: Doc,
    required: Boolean,
    deprecate: Boolean = false,
    allowEmptyValue: Boolean = false,
    content: (String, MediaType),
  ) extends HeaderOrReference {
    override def toJson: String = JsonRenderer.renderFields(
      "description"     -> description,
      "required"        -> required,
      "deprecated"      -> deprecate,
      "allowEmptyValue" -> allowEmptyValue,
      "content"         -> content,
    )
  }

  sealed trait RequestBodyOrReference extends openapi.OpenAPIBase

  /**
   * Describes a single request body.
   *
   * @param description
   *   A brief description of the request body. This could contain examples of
   *   use.
   * @param content
   *   The content of the request body. The key is a media type or [media type
   *   range]appendix-D) and the value describes it. For requests that match
   *   multiple keys, only the most specific key is applicable.
   * @param required
   *   Determines if the request body is required in the request.
   */
  final case class RequestBody(description: Doc, content: Map[String, MediaType], required: Boolean = false)
      extends ResponseOrReference {
    override def toJson: String = JsonRenderer.renderFields(
      "description" -> description,
      "content"     -> content,
      "required"    -> required,
    )
  }

  /**
   * Each Media Type Object provides schema and examples for the media type
   * identified by its key.
   *
   * @param schema
   *   The schema defining the content of the request, response, or parameter.
   * @param examples
   *   Examples of the media type. Each example object SHOULD match the media
   *   type and specified schema if present. If referencing a schema which
   *   contains an example, the examples value SHALL override the example
   *   provided by the schema.
   * @param encoding
   *   A map between a property name and its encoding information. The key,
   *   being the property name, MUST exist in the schema as a property. The
   *   encoding object SHALL only apply to requestBody objects when the media
   *   type is multipart or application/x-www-form-urlencoded.
   */
  final case class MediaType(
    schema: SchemaOrReference,
    examples: Map[String, ExampleOrReference],
    encoding: Map[String, Encoding],
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "schema"   -> schema,
      "examples" -> examples,
      "encoding" -> encoding,
    )
  }

  /**
   * A single encoding definition applied to a single schema property.
   *
   * TODO: default values (https://spec.openapis.org/oas/v3.0.3#encoding-object)
   *
   * @param contentType
   *   The Content-Type for encoding a specific property.
   * @param headers
   *   A map allowing additional information to be provided as headers, for
   *   example Content-Disposition. Content-Type is described separately and
   *   SHALL be ignored in this section. This property SHALL be ignored if the
   *   request body media type is not a multipart.
   * @param style
   *   Describes how a specific property value will be serialized depending on
   *   its type. This property SHALL be ignored if the request body media type
   *   is not application/x-www-form-urlencoded.
   * @param explode
   *   When this is true, property values of type array or object generate
   *   separate parameters for each value of the array, or key-value-pair of the
   *   map.
   * @param allowReserved
   *   Determines whether the parameter value SHOULD allow reserved characters,
   *   as defined by [RFC3986] to be included without percent-encoding. This
   *   property SHALL be ignored if the request body media type is not
   *   application/x-www-form-urlencoded.
   */
  final case class Encoding(
    contentType: String,
    headers: Map[String, HeaderOrReference],
    style: String = "form",
    explode: Boolean,
    allowReserved: Boolean = false,
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "contentType"   -> contentType,
      "headers"       -> headers,
      "style"         -> style,
      "explode"       -> explode,
      "allowReserved" -> allowReserved,
    )
  }

  /**
   * A container for the expected responses of an operation. The container maps
   * a HTTP response code to the expected response. The Responses Object MUST
   * contain at least one response code, and it SHOULD be the response for a
   * successful operation call.
   */
  type Responses = Map[Status, ResponseOrReference]

  sealed trait ResponseOrReference extends openapi.OpenAPIBase

  /**
   * Describes a single response from an API Operation, including design-time,
   * static links to operations based on the response.
   *
   * @param description
   *   A short description of the response.
   * @param headers
   *   Maps a header name to its definition. [RFC7230] states header names are
   *   case insensitive. If a response header is defined with the name
   *   "Content-Type", it SHALL be ignored.
   * @param content
   *   A map containing descriptions of potential response payloads. The key is
   *   a media type or [media type range]appendix-D) and the value describes it.
   *   For responses that match multiple keys, only the most specific key is
   *   applicable.
   * @param links
   *   A map of operations links that can be followed from the response. The key
   *   of the map is a short name for the link, following the naming constraints
   *   of the names for Component Objects.
   */
  final case class Response(
    description: Doc,
    headers: Map[String, HeaderOrReference],
    content: Map[String, MediaType],
    links: Map[String, LinkOrReference],
  ) extends ResponseOrReference {
    override def toJson: String = JsonRenderer.renderFields(
      "description" -> description,
      "headers"     -> headers,
      "content"     -> content,
      "links"       -> links,
    )
  }

  sealed trait CallbackOrReference extends openapi.OpenAPIBase

  /**
   * A map of possible out-of band callbacks related to the parent operation.
   * Each value in the map is a Path Item Object that describes a set of
   * requests that may be initiated by the API provider and the expected
   * responses. The key value used to identify the path item object is an
   * expression, evaluated at runtime, that identifies a URL to use for the
   * callback operation.
   *
   * @param expressions
   *   A Path Item Object used to define a callback request and expected
   *   responses.
   */
  final case class Callback(expressions: Map[String, PathItem]) extends CallbackOrReference {
    override def toJson: String = {
      val toRender = expressions.foldLeft(List.empty[(String, Renderer[PathItem])]) { case (acc, (k, v)) =>
        (k, v: Renderer[PathItem]) :: acc
      }
      JsonRenderer.renderFields(toRender: _*)
    }
  }

  sealed trait ExampleOrReference extends openapi.OpenAPIBase

  /**
   * In all cases, the example value is expected to be compatible with the type
   * schema of its associated value. Tooling implementations MAY choose to
   * validate compatibility automatically, and reject the example value(s) if
   * incompatible.
   *
   * @param summary
   *   Short description for the example.
   * @param description
   *   Long description for the example.
   * @param externalValue
   *   A URL that points to the literal example. This provides the capability to
   *   reference examples that cannot easily be included in JSON or YAML
   *   documents.
   */
  final case class Example(summary: String = "", description: Doc, externalValue: URI) extends ExampleOrReference {
    override def toJson: String = JsonRenderer.renderFields(
      "summary"       -> summary,
      "description"   -> description,
      "externalValue" -> externalValue,
    )
  }

  sealed trait LinkOrReference extends openapi.OpenAPIBase

  /**
   * The Link object represents a possible design-time link for a response. The
   * presence of a link does not guarantee the caller’s ability to successfully
   * invoke it, rather it provides a known relationship and traversal mechanism
   * between responses and other operations.
   *
   * Unlike dynamic links (i.e. links provided in the response payload), the OAS
   * linking mechanism does not require link information in the runtime
   * response.
   *
   * For computing links, and providing instructions to execute them, a runtime
   * expression is used for accessing values in an operation and using them as
   * parameters while invoking the linked operation.
   *
   * @param operationRef
   *   A relative or absolute URI reference to an OAS operation. This field MUST
   *   point to an Operation Object. Relative operationRef values MAY be used to
   *   locate an existing Operation Object in the OpenAPI definition.
   * @param parameters
   *   A map representing parameters to pass to an operation as identified via
   *   operationRef. The key is the parameter name to be used, whereas the value
   *   can be a constant or an expression to be evaluated and passed to the
   *   linked operation. The parameter name can be qualified using the parameter
   *   location [{in}.]{name} for operations that use the same parameter name in
   *   different locations (e.g. path.id).
   * @param requestBody
   *   A literal value or {expression} to use as a request body when calling the
   *   target operation.
   * @param description
   *   A description of the link.
   * @param server
   *   A server object to be used by the target operation.
   */
  final case class Link(
    operationRef: URI,
    parameters: Map[String, LiteralOrExpression],
    requestBody: LiteralOrExpression,
    description: Doc,
    server: Option[Server],
  ) extends LinkOrReference {
    override def toJson: String = JsonRenderer.renderFields(
      "operationRef" -> operationRef,
      "parameters"   -> parameters,
      "requestBody"  -> requestBody,
      "description"  -> description,
      "server"       -> server,
    )
  }

  sealed trait LiteralOrExpression
  object LiteralOrExpression {
    final case class NumberLiteral(value: Long)                   extends LiteralOrExpression
    final case class DecimalLiteral(value: Double)                extends LiteralOrExpression
    final case class StringLiteral(value: String)                 extends LiteralOrExpression
    final case class BooleanLiteral(value: Boolean)               extends LiteralOrExpression
    sealed abstract case class Expression private (value: String) extends LiteralOrExpression

    object Expression {
      private[openapi] def create(value: String): Expression = new Expression(value) {}
    }

    // TODO: maybe one could make a regex to validate the expression. For now just accept anything
    // https://swagger.io/specification/#runtime-expressions
    val ExpressionRegex: Regex = """.*""".r

    def expression(value: String): Option[LiteralOrExpression] =
      value match {
        case ExpressionRegex() => Some(Expression.create(value))
        case _                 => None
      }
  }

  /**
   * Adds metadata to a single tag that is used by the Operation Object. It is
   * not mandatory to have a Tag Object per tag defined in the Operation Object
   * instances.
   *
   * @param name
   *   The name of the tag.
   * @param description
   *   A short description for the tag.
   * @param externalDocs
   *   Additional external documentation for this tag.
   */
  final case class Tag(name: String, description: Doc, externalDocs: URI) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "name"         -> name,
      "description"  -> description,
      "externalDocs" -> externalDocs,
    )
  }

  /**
   * A simple object to allow referencing other components in the specification,
   * internally and externally.
   *
   * @param ref
   *   The reference string.
   */
  final case class Reference(ref: String)
      extends SchemaOrReference
      with ResponseOrReference
      with ParameterOrReference
      with ExampleOrReference
      with RequestBodyOrReference
      with HeaderOrReference
      with SecuritySchemeOrReference
      with LinkOrReference
      with CallbackOrReference {
    override def toJson: String = JsonRenderer.renderFields(s"$$ref" -> ref)
  }

  sealed trait SchemaOrReference extends openapi.OpenAPIBase

  sealed trait Schema extends openapi.OpenAPIBase with SchemaOrReference {
    def nullable: Boolean
    def discriminator: Option[Discriminator]
    def readOnly: Boolean
    def writeOnly: Boolean
    def xml: Option[XML]
    def externalDocs: URI
    def example: String
    def deprecated: Boolean

    override def toJson: String =
      JsonRenderer.renderFields(
        "nullable"      -> nullable,
        "discriminator" -> discriminator,
        "readOnly"      -> readOnly,
        "writeOnly"     -> writeOnly,
        "xml"           -> xml,
        "externalDocs"  -> externalDocs,
        "example"       -> example,
        "deprecated"    -> deprecated,
      )
  }

  object Schema {

    /**
     * The Schema Object allows the definition of input and output data types.
     *
     * Marked as readOnly. This means that it MAY be sent as part of a response
     * but SHOULD NOT be sent as part of the request. If the property is in the
     * required list, the required will take effect on the response only.
     *
     * @param nullable
     *   A true value adds "null" to the allowed type specified by the type
     *   keyword, only if type is explicitly defined within the same Schema
     *   Object. Other Schema Object constraints retain their defined behavior,
     *   and therefore may disallow the use of null as a value. A false value
     *   leaves the specified or default type unmodified.
     * @param discriminator
     *   Adds support for polymorphism. The discriminator is an object name that
     *   is used to differentiate between other schemas which may satisfy the
     *   payload description.
     * @param xml
     *   This MAY be used only on properties schemas. It has no effect on root
     *   schemas. Adds additional metadata to describe the XML representation of
     *   this property.
     * @param externalDocs
     *   Additional external documentation for this schema.
     * @param example
     *   A free-form property to include an example of an instance for this
     *   schema.
     * @param deprecated
     *   Specifies that a schema is deprecated and SHOULD be transitioned out of
     *   usage.
     */
    final case class ResponseSchema(
      nullable: Boolean = false,
      discriminator: Option[Discriminator],
      xml: Option[XML],
      externalDocs: URI,
      example: String,
      deprecated: Boolean = false,
    ) extends Schema
        with Parameter.Definition {
      def readOnly: Boolean  = true
      def writeOnly: Boolean = false
    }

    /**
     * The Schema Object allows the definition of input and output data types.
     *
     * Marked as writeOnly. This means that it MAY be sent as part of a request
     * but SHOULD NOT be sent as part of the response. If the property is in the
     * required list, the required will take effect on the request only.
     *
     * @param nullable
     *   A true value adds "null" to the allowed type specified by the type
     *   keyword, only if type is explicitly defined within the same Schema
     *   Object. Other Schema Object constraints retain their defined behavior,
     *   and therefore may disallow the use of null as a value. A false value
     *   leaves the specified or default type unmodified.
     * @param discriminator
     *   Adds support for polymorphism. The discriminator is an object name that
     *   is used to differentiate between other schemas which may satisfy the
     *   payload description.
     * @param xml
     *   This MAY be used only on properties schemas. It has no effect on root
     *   schemas. Adds additional metadata to describe the XML representation of
     *   this property.
     * @param externalDocs
     *   Additional external documentation for this schema.
     * @param example
     *   A free-form property to include an example of an instance for this
     *   schema.
     * @param deprecated
     *   Specifies that a schema is deprecated and SHOULD be transitioned out of
     *   usage.
     */
    final case class RequestSchema(
      nullable: Boolean = false,
      discriminator: Option[Discriminator],
      xml: Option[XML],
      externalDocs: URI,
      example: String,
      deprecated: Boolean = false,
    ) extends Schema
        with Parameter.Definition {
      def readOnly: Boolean  = false
      def writeOnly: Boolean = true
    }
  }

  /**
   * When request bodies or response payloads may be one of a number of
   * different schemas, a discriminator object can be used to aid in
   * serialization, deserialization, and validation. The discriminator is a
   * specific object in a schema which is used to inform the consumer of the
   * specification of an alternative schema based on the value associated with
   * it.
   *
   * When using the discriminator, inline schemas will not be considered.
   *
   * @param propertyName
   *   The name of the property in the payload that will hold the discriminator
   *   value.
   * @param mapping
   *   An object to hold mappings between payload values and schema names or
   *   references.
   */
  final case class Discriminator(propertyName: String, mapping: Map[String, String]) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "propertyName" -> propertyName,
      "mapping"      -> mapping,
    )
  }

  /**
   * A metadata object that allows for more fine-tuned XML model definitions.
   *
   * When using arrays, XML element names are not inferred (for singular/plural
   * forms) and the name property SHOULD be used to add that information.
   *
   * @param name
   *   Replaces the name of the element/attribute used for the described schema
   *   property. When defined within items, it will affect the name of the
   *   individual XML elements within the list. When defined alongside type
   *   being array (outside the items), it will affect the wrapping element and
   *   only if wrapped is true. If wrapped is false, it will be ignored.
   * @param namespace
   *   The URI of the namespace definition.
   * @param prefix
   *   The prefix to be used for the name.
   * @param attribute
   *   Declares whether the property definition translates to an attribute
   *   instead of an element.
   * @param wrapped
   *   MAY be used only for an array definition. Signifies whether the array is
   *   wrapped (for example, <books><book/><book/></books>) or unwrapped
   *   (<book/><book/>). The definition takes effect only when defined alongside
   *   type being array (outside the items).
   */
  final case class XML(name: String, namespace: URI, prefix: String, attribute: Boolean = false, wrapped: Boolean)
      extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "name"      -> name,
      "namespace" -> namespace,
      "prefix"    -> prefix,
      "attribute" -> attribute,
      "wrapped"   -> wrapped,
    )
  }

  sealed trait SecuritySchemeOrReference extends openapi.OpenAPIBase

  sealed trait SecurityScheme extends SecuritySchemeOrReference {
    def `type`: String
    def description: Doc
  }

  object SecurityScheme {

    /**
     * Defines an HTTP security scheme that can be used by the operations.
     *
     * @param description
     *   A short description for security scheme.
     * @param name
     *   The name of the header, query or cookie parameter to be used.
     * @param in
     *   The location of the API key.
     */
    final case class ApiKey(description: Doc, name: String, in: ApiKey.In) extends SecurityScheme {
      override def `type`: String = "apiKey"

      override def toJson: String =
        JsonRenderer.renderFields(
          "type"        -> `type`,
          "description" -> description,
          "name"        -> name,
          "in"          -> in,
        )
    }

    object ApiKey {
      sealed trait In extends openapi.OpenAPIBase {
        self: Product =>
        override def toJson: String =
          s""""${self.productPrefix.updated(0, self.productPrefix.charAt(0).toLower)}""""
      }

      object In {
        case object Query  extends In
        case object Header extends In
        case object Cookie extends In
      }
    }

    /**
     * @param description
     *   A short description for security scheme.
     * @param scheme
     *   The name of the HTTP Authorization scheme to be used in the
     *   Authorization header as defined in [RFC7235]. The values used SHOULD be
     *   registered in the IANA Authentication Scheme registry.
     * @param bearerFormat
     *   A hint to the client to identify how the bearer token is formatted.
     *   Bearer tokens are usually generated by an authorization server, so this
     *   information is primarily for documentation purposes.
     */
    final case class Http(description: Doc, scheme: String, bearerFormat: Option[String]) extends SecurityScheme {
      override def `type`: String = "http"

      override def toJson: String =
        JsonRenderer.renderFields(
          "type"         -> `type`,
          "description"  -> description,
          "scheme"       -> scheme,
          "bearerFormat" -> bearerFormat,
        )
    }

    /**
     * @param description
     *   A short description for security scheme.
     * @param flows
     *   An object containing configuration information for the flow types
     *   supported.
     */
    final case class OAuth2(description: Doc, flows: OAuthFlows) extends SecurityScheme {
      override def `type`: String = "oauth2"

      override def toJson: String =
        JsonRenderer.renderFields(
          "type"        -> `type`,
          "description" -> description,
          "flows"       -> flows,
        )
    }

    /**
     * @param description
     *   A short description for security scheme.
     * @param openIdConnectUrl
     *   OpenId Connect URL to discover OAuth2 configuration values.
     */
    final case class OpenIdConnect(description: Doc, openIdConnectUrl: URI) extends SecurityScheme {
      override def `type`: String = "openIdConnect"

      override def toJson: String =
        JsonRenderer.renderFields(
          "type"             -> `type`,
          "description"      -> description,
          "openIdConnectUrl" -> openIdConnectUrl,
        )
    }
  }

  /**
   * Allows configuration of the supported OAuth Flows.
   *
   * @param `implicit`
   *   Configuration for the OAuth Implicit flow.
   * @param password
   *   Configuration for the OAuth Resource Owner Password flow
   * @param clientCredentials
   *   Configuration for the OAuth Client Credentials flow. Previously called
   *   application in OpenAPI 2.0.
   * @param authorizationCode
   *   Configuration for the OAuth Authorization Code flow. Previously called
   *   accessCode in OpenAPI 2.0.
   */
  final case class OAuthFlows(
    `implicit`: Option[OAuthFlow.Implicit],
    password: Option[OAuthFlow.Password],
    clientCredentials: Option[OAuthFlow.ClientCredentials],
    authorizationCode: Option[OAuthFlow.AuthorizationCode],
  ) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "implicit"          -> `implicit`,
      "password"          -> password,
      "clientCredentials" -> clientCredentials,
      "authorizationCode" -> authorizationCode,
    )
  }

  sealed trait OAuthFlow extends openapi.OpenAPIBase {
    def refreshUrl: Option[URI]
    def scopes: Map[String, String]
  }

  object OAuthFlow {

    /**
     * Configuration for the OAuth Implicit flow.
     *
     * @param authorizationUrl
     *   The authorization URL to be used for this flow.
     * @param refreshUrl
     *   The URL to be used for obtaining refresh tokens.
     * @param scopes
     *   The available scopes for the OAuth2 security scheme. A map between the
     *   scope name and a short description for it. The map MAY be empty.
     */
    final case class Implicit(authorizationUrl: URI, refreshUrl: Option[URI], scopes: Map[String, String])
        extends OAuthFlow {
      override def toJson: String = JsonRenderer.renderFields(
        "authorizationUrl" -> authorizationUrl,
        "refreshUrl"       -> refreshUrl,
        "scopes"           -> scopes,
      )
    }

    /**
     * Configuration for the OAuth Authorization Code flow. Previously called
     * accessCode in OpenAPI 2.0.
     *
     * @param authorizationUrl
     *   The authorization URL to be used for this flow.
     * @param refreshUrl
     *   The URL to be used for obtaining refresh tokens.
     * @param scopes
     *   The available scopes for the OAuth2 security scheme. A map between the
     *   scope name and a short description for it. The map MAY be empty.
     * @param tokenUrl
     *   The token URL to be used for this flow.
     */
    final case class AuthorizationCode(
      authorizationUrl: URI,
      refreshUrl: Option[URI],
      scopes: Map[String, String],
      tokenUrl: URI,
    ) extends OAuthFlow {
      override def toJson: String = JsonRenderer.renderFields(
        "authorizationUrl" -> authorizationUrl,
        "refreshUrl"       -> refreshUrl,
        "scopes"           -> scopes,
        "tokenUrl"         -> tokenUrl,
      )
    }

    /**
     * Configuration for the OAuth Resource Owner Password flow.
     *
     * @param refreshUrl
     *   The URL to be used for obtaining refresh tokens.
     * @param scopes
     *   The available scopes for the OAuth2 security scheme. A map between the
     *   scope name and a short description for it. The map MAY be empty.
     * @param tokenUrl
     *   The token URL to be used for this flow.
     */
    final case class Password(refreshUrl: Option[URI], scopes: Map[String, String], tokenUrl: URI) extends OAuthFlow {
      override def toJson: String = JsonRenderer.renderFields(
        "refreshUrl" -> refreshUrl,
        "scopes"     -> scopes,
        "tokenUrl"   -> tokenUrl,
      )
    }

    /**
     * Configuration for the OAuth Client Credentials flow. Previously called
     * application in OpenAPI 2.0.
     *
     * @param refreshUrl
     *   The URL to be used for obtaining refresh tokens.
     * @param scopes
     *   The available scopes for the OAuth2 security scheme. A map between the
     *   scope name and a short description for it. The map MAY be empty.
     * @param tokenUrl
     *   The token URL to be used for this flow.
     */
    final case class ClientCredentials(refreshUrl: Option[URI], scopes: Map[String, String], tokenUrl: URI)
        extends OAuthFlow {
      override def toJson: String = JsonRenderer.renderFields(
        "refreshUrl" -> refreshUrl,
        "scopes"     -> scopes,
        "tokenUrl"   -> tokenUrl,
      )
    }
  }

  /**
   * Lists the required security schemes to execute this operation. The name
   * used for each property MUST correspond to a security scheme declared in the
   * Security Schemes under the Components Object.
   *
   * Security Requirement Objects that contain multiple schemes require that all
   * schemes MUST be satisfied for a request to be authorized. This enables
   * support for scenarios where multiple query parameters or HTTP headers are
   * required to convey security information.
   *
   * When a list of Security Requirement Objects is defined on the OpenAPI
   * Object or Operation Object, only one of the Security Requirement Objects in
   * the list needs to be satisfied to authorize the request.
   *
   * @param securitySchemes
   *   If the security scheme is of type "oauth2" or "openIdConnect", then the
   *   value is a list of scope names required for the execution, and the list
   *   MAY be empty if authorization does not require a specified scope. For
   *   other security scheme types, the List MUST be empty.
   */
  final case class SecurityRequirement(securitySchemes: Map[String, List[String]]) extends openapi.OpenAPIBase {
    override def toJson: String = JsonRenderer.renderFields(
      "securitySchemes" -> securitySchemes,
    )
  }
}
