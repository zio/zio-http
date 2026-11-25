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

package zio.http.endpoint.openapi

import java.net.URI

import scala.annotation.nowarn
import scala.collection.immutable.ListMap
import scala.util.matching.Regex

import zio.Chunk
import zio.json.ast._

import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.JsonCodec
import zio.schema.codec.JsonCodec.ExplicitConfig
import zio.schema.codec.json._

import zio.http.Status
import zio.http.codec.Doc
import zio.http.endpoint.openapi.OpenAPI.SecurityScheme.SecurityRequirement

import com.lihaoyi.unroll

/**
 * The root object of an OpenAPI document (the OpenAPI Object): the API
 * metadata, its paths and operations, reusable components, security
 * requirements and tags. `OpenAPIGen.fromEndpoints` builds one from endpoints,
 * `toJson` and `toJsonPretty` render it as JSON, and `OpenAPI.fromJson` parses
 * one.
 *
 * {{{
 * import zio.http.Status
 * import zio.http.codec.Doc
 * import zio.http.endpoint.openapi.OpenAPI
 * import zio.http.endpoint.openapi.OpenAPI.{Operation, Path, PathItem, ReferenceOr, Response, StatusOrDefault}
 *
 * val api: OpenAPI = OpenAPI.empty
 *   .title("Weather API")
 *   .version("1.0.0")
 *   .path(
 *     Path.fromString("/forecasts").get,
 *     PathItem.empty.addGet(
 *       Operation(
 *         summary = Some("List forecasts"),
 *         description = None,
 *         externalDocs = None,
 *         operationId = Some("listForecasts"),
 *         requestBody = None,
 *         responses = Map(
 *           StatusOrDefault.StatusValue(Status.Ok) ->
 *             ReferenceOr.Or(Response(description = Some(Doc.p("Forecasts"))))
 *         )
 *       )
 *     )
 *   )
 *
 * val json: String = api.toJson
 * // {"openapi":"3.1.0","info":{"title":"Weather API","version":"1.0.0"},"paths":{"/forecasts":{"get":{"summary":"List forecasts", ...
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/endpoint/ Endpoint]], whose OpenAPI
 *   section shows how to generate a document from endpoints
 * @see
 *   [[https://zio.dev/zio-http/reference/openapi-gen-sbt-plugin/ OpenAPI Gen sbt plugin]],
 *   which generates endpoint code from an OpenAPI document
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
 *   target server. An empty list is left out of the JSON, and the OpenAPI
 *   specification then reads the servers as one Server Object with a url value
 *   of /.
 * @param paths
 *   The available paths and operations for the API. The JSON lists the paths
 *   sorted by name.
 * @param components
 *   An element to hold various schemas for the specification.
 * @param security
 *   A declaration of which security mechanisms can be used across the API. The
 *   list of values includes alternative security requirement objects that can
 *   be used. Only one of the security requirement objects need to be satisfied
 *   to authorize a request. Individual operations can override this definition.
 *   To make security optional, an empty security requirement ({}) can be
 *   included in the List.
 * @param tags
 *   A list of tags used by the specification with additional metadata. The
 *   order of the tags can be used to reflect on their order by the parsing
 *   tools. Not all tags that are used by the Operation Object must be declared.
 *   The tags that are not declared MAY be organized randomly or based on the
 *   tools’ logic. Each tag name in the list MUST be unique.
 * @param externalDocs
 *   Additional external documentation.
 */
final case class OpenAPI(
  openapi: String,
  info: OpenAPI.Info,
  servers: List[OpenAPI.Server] = List.empty,
  paths: ListMap[OpenAPI.Path, OpenAPI.PathItem] = ListMap.empty,
  components: Option[OpenAPI.Components],
  security: List[SecurityRequirement] = List.empty,
  tags: List[OpenAPI.Tag] = List.empty,
  externalDocs: Option[OpenAPI.ExternalDoc],
) {

  /**
   * Merges `other` into this document. The result keeps `openapi`, `info` and
   * `externalDocs` of this document, appends the `servers`, `security` and
   * `tags` of `other` (duplicates are kept), and merges `components` with
   * `Components.++`.
   *
   * For a path in both documents, the merged path item keeps each operation of
   * this document and takes from `other` only the methods that are missing
   * here. It keeps this item's description when this item has an operation,
   * appends the other item's description when the other item adds a method, and
   * combines the servers, parameters and extensions of both.
   */
  def ++(other: OpenAPI): OpenAPI = OpenAPI(
    openapi = openapi,
    info = info,
    servers = servers ++ other.servers,
    paths = mergePaths(paths, other.paths),
    components = (components.toSeq ++ other.components).reduceOption(_ ++ _),
    security = security ++ other.security,
    tags = tags ++ other.tags,
    externalDocs = externalDocs,
  )

  private def mergePaths(paths: ListMap[OpenAPI.Path, OpenAPI.PathItem]*): ListMap[OpenAPI.Path, OpenAPI.PathItem] =
    ListMap(
      paths
        .flatMap(_.toSeq)
        .groupBy(_._1)
        .map { case (path, pathItems) =>
          val pathItem = pathItems.map(_._2).reduce { (i, j) =>
            var docI    = Doc.empty
            var docJ    = Doc.empty
            var get     = i.get
            var put     = i.put
            var post    = i.post
            var delete  = i.delete
            var options = i.options
            var head    = i.head
            var patch   = i.patch
            var trace   = i.trace

            if (
              get.isDefined || put.isDefined || post.isDefined || delete.isDefined || options.isDefined || head.isDefined || patch.isDefined || trace.isDefined
            ) {
              docI = i.description.getOrElse(Doc.empty)
            }
            if (
              (get.isEmpty && j.get.isDefined) || (put.isEmpty && j.put.isDefined) || (post.isEmpty && j.post.isDefined) || (delete.isEmpty && j.delete.isDefined) || (options.isEmpty && j.options.isDefined) || (head.isEmpty && j.head.isDefined) || (patch.isEmpty && j.patch.isDefined) || (trace.isEmpty && j.trace.isDefined)
            ) {
              docJ = j.description.getOrElse(Doc.empty)
            }
            get = get.orElse(j.get)
            put = put.orElse(j.put)
            post = post.orElse(j.post)
            delete = delete.orElse(j.delete)
            options = options.orElse(j.options)
            head = head.orElse(j.head)
            patch = patch.orElse(j.patch)
            trace = trace.orElse(j.trace)

            i.copy(
              get = get,
              put = put,
              post = post,
              delete = delete,
              options = options,
              head = head,
              patch = patch,
              trace = trace,
              description = Some(docI + docJ).filter(!_.isEmpty),
              servers = i.servers ++ j.servers,
              parameters = i.parameters ++ j.parameters,
              extensions = i.extensions ++ j.extensions,
            )
          }
          (path, pathItem)
        }
        .toSeq: _*,
    )

  /**
   * Returns a copy with `pathItem` added at `path`. When `path` already has a
   * path item, the two are merged as in `++` with `pathItem` first, so an
   * operation of `pathItem` replaces the existing operation for the same
   * method.
   */
  def path(path: OpenAPI.Path, pathItem: OpenAPI.PathItem): OpenAPI =
    copy(paths = mergePaths(ListMap(path -> pathItem), paths))

  /**
   * Encodes the document as compact JSON. Fields that are `None` and empty
   * collections are left out, paths and component names are sorted by name,
   * `Doc` descriptions are written as CommonMark strings, and extensions are
   * written as `x-` fields.
   */
  def toJson: String =
    JsonCodec
      .jsonEncoder(
        JsonCodec.Configuration(
          explicitEmptyCollections = ExplicitConfig(encoding = false),
          explicitNulls = ExplicitConfig(encoding = false),
        ),
      )(OpenAPI.schema)
      .encodeJson(this, None)
      .toString

  /**
   * Encodes the document as indented JSON, with the same fields as `toJson`.
   */
  def toJsonPretty: String =
    JsonCodec
      .jsonEncoder(
        JsonCodec.Configuration(
          explicitEmptyCollections = ExplicitConfig(encoding = false),
          explicitNulls = ExplicitConfig(encoding = false),
        ),
      )(OpenAPI.schema)
      .encodeJson(this, Some(0))
      .toString

  /** Returns a copy with `info.title` set to `title`. */
  def title(title: String): OpenAPI = copy(info = info.copy(title = title))

  /**
   * Returns a copy with `info.version` set to `version`, the version of the API
   * document (not the OpenAPI Specification version in `openapi`).
   */
  def version(version: String): OpenAPI = copy(info = info.copy(version = version))
}

/**
 * The companion of [[OpenAPI]]: its schema, `fromJson`, `empty`, and the types
 * of the OpenAPI document model, such as `Info`, `PathItem`, `Operation` and
 * `Components`.
 */
object OpenAPI {

  /**
   * The schema that `toJson`, `toJsonPretty` and `fromJson` use to encode and
   * decode a document.
   */
  implicit val schema: Schema[OpenAPI] =
    DeriveSchema.gen[OpenAPI]

  /**
   * Decodes a document from JSON. Returns `Left` with the error message when
   * the input is not a valid document: for example `Unexpected end of input`
   * for `{`, and `.info(missing)` when the `info` field is missing.
   *
   * An `x-` field of the root object is ignored. Descriptions are read back as
   * raw CommonMark `Doc` values, so `fromJson(api.toJson)` is not equal to
   * `api` when `api` has a description built with `Doc.p`.
   */
  def fromJson(json: String): Either[String, OpenAPI] =
    JsonCodec
      .jsonDecoder(OpenAPI.schema)
      .decodeJson(json)

  /**
   * A document with `openapi` set to `3.1.0`, an empty title and version, and
   * no servers, paths, components, security requirements, tags or external
   * docs. `toJson` renders it as
   * `{"openapi":"3.1.0","info":{"title":"","version":""}}`.
   */
  def empty: OpenAPI = OpenAPI(
    openapi = "3.1.0",
    info = Info(
      title = "",
      description = None,
      termsOfService = None,
      contact = None,
      license = None,
      version = "",
      extensions = Extensions.empty,
    ),
    servers = List.empty,
    paths = ListMap.empty,
    components = None,
    security = List.empty,
    tags = List.empty,
    externalDocs = None,
  )

  private def extensionSchema[A](
    rawSchema: Schema[A],
    getExtensions: A => Extensions,
    build: (A, Extensions) => A,
  ): Schema[A] = {
    lazy val encoder = JsonCodec.jsonEncoder(
      JsonCodec.Configuration(
        explicitEmptyCollections = ExplicitConfig(encoding = false),
        explicitNulls = ExplicitConfig(encoding = false),
      ),
    )(rawSchema)
    lazy val decoder = JsonCodec.jsonDecoder(rawSchema)

    Schema[Json].transformOrFail[A](
      {
        case Json.Obj(fields) =>
          val (ext, rest) = fields.partition(_._1.startsWith("x-"))
          val exts        = ext.foldLeft[Map[String, Json]](Map.empty) { case (acc, (k, v)) =>
            acc.updated(k.stripPrefix("x-"), v)
          }
          decoder.fromJsonAST(Json.Obj(rest)).map(v => build(v, Extensions(exts)))
        case _                => Left("Expected an object")
      },
      a =>
        encoder.toJsonAST(a).map {
          case Json.Obj(fields) =>
            val exts = getExtensions(a).underlying.toList.sortBy(_._1).collect { case (k, v) =>
              s"x-$k" -> v
            }
            Json.Obj(fields ++ Chunk.fromIterable(exts))
          case other            => other
        },
    )
  }

  /**
   * A schema for `zio.http.Status` that reads and writes the status code as a
   * JSON string, such as `"404"`. Decoding a string that is not a number, such
   * as `"abc"`, throws a `java.lang.NumberFormatException` instead of returning
   * a `Left`.
   */
  implicit def statusSchema: Schema[Status] =
    zio.schema
      .Schema[String]
      .transform[Status](
        s => Status.fromInt(s.toInt),
        p => p.text,
      )

  /**
   * The schema of the `paths` field: a JSON object that maps each path to its
   * `PathItem`. Encoding writes the paths sorted by name. Decoding fails with
   * `Invalid path: nopath` for the key `nopath`, or any other key that
   * `Path.fromString` rejects.
   */
  implicit def pathMapSchema: Schema[ListMap[Path, PathItem]] =
    DeriveSchema
      .gen[Map[String, PathItem]]
      .transformOrFail(
        m => {
          val it                                           = m.iterator
          var transformed                                  = ListMap.empty[Path, PathItem]
          var error: Left[String, ListMap[Path, PathItem]] = null
          while (it.hasNext && error == null) {
            val (k, v) = it.next()
            Path.fromString(k) match {
              case Some(path) => transformed += path -> v
              case None       => error = Left(s"Invalid path: $k")
            }
          }
          if (error != null) error
          else Right(transformed)
        },
        (m: Map[Path, PathItem]) => Right(ListMap(m.toSeq.sortBy(_._1.name).map { case (k, v) => k.name -> v }: _*)),
      )

  /**
   * A schema for a map keyed by component name, as used by `Components`: a JSON
   * object from each `Key` to a `T`. Encoding and decoding both sort the
   * entries by name. Decoding fails with `Invalid key: a b` for the key `a b`,
   * or any other key that `Key.fromString` rejects.
   */
  implicit def keyMapSchema[T](implicit
    schema: Schema[T],
  ): Schema[ListMap[Key, T]] =
    Schema
      .map[String, T]
      .transformOrFail(
        m => {
          val it                                   = m.iterator
          var transformed                          = Vector.empty[(Key, T)]
          var error: Left[String, ListMap[Key, T]] = null
          while (it.hasNext && error == null) {
            val (k, v) = it.next()
            Key.fromString(k) match {
              case Some(key) => transformed :+= key -> v
              case None      => error = Left(s"Invalid key: $k")
            }
          }
          if (error != null) error
          else Right(ListMap(transformed.sortBy(_._1.name): _*))
        },
        (m: Map[Key, T]) => Right(ListMap(m.toSeq.sortBy(_._1.name).map { case (k, v) => k.name -> v }: _*)),
      )

  /**
   * A schema for the responses map of an operation: a JSON object keyed by a
   * status code, such as `404`, or by `default`. Encoding writes each key with
   * `StatusOrDefault.text`. Decoding fails with `Invalid status: abc` for the
   * key `abc`.
   */
  implicit def statusMapSchema[T](implicit
    schema: Schema[T],
  ): Schema[Map[StatusOrDefault, T]] =
    Schema
      .map[String, T]
      .transformOrFail(
        m => {
          val it                                           = m.iterator
          var transformed                                  = Map.empty[StatusOrDefault, T]
          var error: Left[String, Map[StatusOrDefault, T]] = null
          while (it.hasNext && error == null) {
            val (k, v) = it.next()
            if (k == "default") transformed += StatusOrDefault.Default -> v
            else {
              zio.http.Status.fromString(k) match {
                case Some(key) => transformed += StatusOrDefault.StatusValue(key) -> v
                case None      => error = Left(s"Invalid status: $k")
              }
            }
          }
          if (error != null) error
          else Right(transformed)
        },
        (m: Map[StatusOrDefault, T]) => Right(m.map { case (k, v) => k.text -> v }),
      )

  /**
   * A schema for one media type name and its `MediaType`, as in the `content`
   * field of a `Parameter`: a JSON object with a single entry. Decoding an
   * object with no entry or with two entries fails with `Invalid media type`.
   */
  implicit def mediaTypeTupleSchema: Schema[(String, MediaType)] =
    zio.schema
      .Schema[Map[String, MediaType]]
      .transformOrFail(
        m => {
          if (m.size == 1) {
            val (k, v) = m.head
            Right((k, v))
          } else Left("Invalid media type")
        },
        t => Right(Map(t._1 -> t._2)),
      )

  /**
   * A schema for a `SecurityRequirement`: a JSON object that maps each security
   * scheme name to a list of scope names, such as `{"oauth":["read"]}`.
   */
  implicit def securityRequirementSchema: Schema[SecurityRequirement] = {
    zio.schema
      .Schema[Map[String, List[String]]]
      .transform[SecurityRequirement](
        m => SecurityRequirement(m),
        s => s.securitySchemes,
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
  final case class ExternalDoc(description: Option[Doc], url: URI)

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
   * @param extensions
   *   Extensions to the info section, written as `x-` fields of the Info
   *   Object. The keys are stored without the `x-` prefix.
   */
  final case class Info(
    title: String,
    description: Option[Doc],
    termsOfService: Option[URI],
    contact: Option[Contact],
    license: Option[License],
    version: String,
    @transientField
    @unroll
    extensions: Extensions = Extensions.empty,
  )

  /** The companion of `Info`, with its schema. */
  object Info extends InfoCompatibility {

    /**
     * The schema of `Info`. It reads each `x-` field into `extensions`, and
     * writes `extensions` as `x-` fields sorted by key.
     */
    implicit val schema: Schema[Info] = extensionSchema(
      DeriveSchema.gen[Info],
      _.extensions,
      (i, e) => i.copy(extensions = e),
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
  final case class Contact(name: Option[String], url: Option[URI], email: Option[String])

  /**
   * License information for the exposed API.
   *
   * @param name
   *   The license name used for the API.
   * @param url
   *   A URL to the license used for the API.
   */
  final case class License(name: String, url: Option[URI])

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
  final case class Server(
    url: URI,
    description: Option[Doc],
    variables: Map[String, ServerVariable] = Map.empty,
  )

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
  final case class ServerVariable(`enum`: Chunk[String], default: String, description: Doc)

  /** The companion of `ServerVariable`, with its schema. */
  object ServerVariable {

    /** The schema of `ServerVariable`, derived from its fields. */
    implicit val schema: Schema[ServerVariable] =
      DeriveSchema.gen[ServerVariable]
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
    schemas: ListMap[Key, ReferenceOr[JsonSchema]] = ListMap.empty,
    responses: ListMap[Key, ReferenceOr[Response]] = ListMap.empty,
    parameters: ListMap[Key, ReferenceOr[Parameter]] = ListMap.empty,
    examples: ListMap[Key, ReferenceOr[Example]] = ListMap.empty,
    requestBodies: ListMap[Key, ReferenceOr[RequestBody]] = ListMap.empty,
    headers: ListMap[Key, ReferenceOr[Header]] = ListMap.empty,
    securitySchemes: ListMap[Key, ReferenceOr[SecurityScheme]] = ListMap.empty,
    links: ListMap[Key, ReferenceOr[Link]] = ListMap.empty,
    callbacks: ListMap[Key, ReferenceOr[Callback]] = ListMap.empty,
  ) {

    /**
     * Merges two `Components` map by map. When both have an entry with the same
     * key, the entry of `other` is kept.
     */
    def ++(other: Components): Components = Components(
      schemas = schemas ++ other.schemas,
      responses = responses ++ other.responses,
      parameters = parameters ++ other.parameters,
      examples = examples ++ other.examples,
      requestBodies = requestBodies ++ other.requestBodies,
      headers = headers ++ other.headers,
      securitySchemes = securitySchemes ++ other.securitySchemes,
      links = links ++ other.links,
      callbacks = callbacks ++ other.callbacks,
    )
  }

  /**
   * The name of a reusable component in `Components`, such as a schema name.
   * Create one with `Key.fromString`, which checks the name against
   * `Key.validName`.
   */
  sealed abstract case class Key private (name: String)

  /** The companion of `Key`, with its schema and constructor. */
  object Key {

    /**
     * The schema of `Key`, as a JSON string. Decoding fails with
     * `Invalid Key a b` for the string `a b`, or any other name that
     * `fromString` rejects.
     */
    implicit val schema: Schema[Key] =
      zio.schema
        .Schema[String]
        .transformOrFail[Key](
          s => fromString(s).toRight(s"Invalid Key $s"),
          p => Right(p.name),
        )

    /**
     * The pattern that the keys of the Components Object must match: one or
     * more ASCII letters, digits, `.`, `-` or `_`.
     */
    val validName: Regex = "^[a-zA-Z0-9.\\-_]+$".r

    /**
     * Returns the `Key` for `name`, or `None` when `name` does not match
     * `validName`: for example `None` for an empty name, `a b`, `a/b` or `é`.
     */
    def fromString(name: String): Option[Key] = name match {
      case validName() => Some(new Key(name) {})
      case _           => None
    }
  }

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
  case class Path private (name: String)

  /** The companion of `Path`, with its schema and constructor. */
  object Path {

    /**
     * The schema of `Path`, as a JSON string. Decoding fails with
     * `Invalid Path x` for the string `x`, or any other string that
     * `fromString` rejects.
     */
    implicit val schema: Schema[Path] = Schema[String].transformOrFail[Path](
      s => fromString(s).toRight(s"Invalid Path $s"),
      p => Right(p.name),
    )

    // todo maybe not the best regex, but the old one was not working at all
    // safe chars from RFC1738 "$" | "-" | "_" | "." | "+"
    private val validPath: Regex = """\/[\/a-zA-Z0-9\-_{}$.+]*""".r

    /**
     * Returns the `Path` for `name`, or `None` unless `name` starts with `/`
     * and has only ASCII letters, digits, and the characters `/`, `-`, `_`,
     * `{`, `}`, `\$`, `.` and `+`. For example `/users/{id}` and `/` are
     * accepted, and `users`, the empty string, `/a b`, `/a?x` and `/~x` are
     * rejected.
     */
    def fromString(name: String): Option[Path] = name match {
      case validPath() => Some(Path(name))
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
   * @param extensions
   *   Extensions to the path item, written as `x-` fields of the Path Item
   *   Object. The keys are stored without the `x-` prefix.
   */
  @nowarn("msg=possible missing interpolator")
  final case class PathItem(
    @fieldName("$ref") ref: Option[String],
    summary: Option[String],
    description: Option[Doc],
    get: Option[Operation],
    put: Option[Operation],
    post: Option[Operation],
    delete: Option[Operation],
    options: Option[Operation],
    head: Option[Operation],
    patch: Option[Operation],
    trace: Option[Operation],
    servers: List[Server] = List.empty,
    parameters: Set[ReferenceOr[Parameter]] = Set.empty,
    @transientField
    @unroll
    extensions: Extensions = Extensions.empty,
  ) {

    /** Returns a copy with `operation` as the GET operation, replacing any. */
    def addGet(operation: Operation): PathItem = copy(get = Some(operation))

    /** Returns a copy with `operation` as the PUT operation, replacing any. */
    def addPut(operation: Operation): PathItem = copy(put = Some(operation))

    /** Returns a copy with `operation` as the POST operation, replacing any. */
    def addPost(operation: Operation): PathItem = copy(post = Some(operation))

    /**
     * Returns a copy with `operation` as the DELETE operation, replacing any.
     */
    def addDelete(operation: Operation): PathItem = copy(delete = Some(operation))

    /**
     * Returns a copy with `operation` as the OPTIONS operation, replacing any.
     */
    def addOptions(operation: Operation): PathItem = copy(options = Some(operation))

    /** Returns a copy with `operation` as the HEAD operation, replacing any. */
    def addHead(operation: Operation): PathItem = copy(head = Some(operation))

    /**
     * Returns a copy with `operation` as the PATCH operation, replacing any.
     */
    def addPatch(operation: Operation): PathItem = copy(patch = Some(operation))

    /**
     * Returns a copy with `operation` as the TRACE operation, replacing any.
     */
    def addTrace(operation: Operation): PathItem = copy(trace = Some(operation))

    /**
     * Returns a copy with `operation` set for all eight methods: GET, PUT,
     * POST, DELETE, OPTIONS, HEAD, PATCH and TRACE.
     */
    def any(operation: Operation): PathItem =
      copy(
        get = Some(operation),
        put = Some(operation),
        post = Some(operation),
        delete = Some(operation),
        options = Some(operation),
        head = Some(operation),
        patch = Some(operation),
        trace = Some(operation),
      )
  }

  /** The companion of `PathItem`, with its schema and `empty`. */
  object PathItem {

    /**
     * The schema of `PathItem`. It reads each `x-` field into `extensions`, and
     * writes `extensions` as `x-` fields sorted by key.
     */
    implicit val schema: Schema[PathItem] = extensionSchema(
      DeriveSchema.gen[PathItem],
      _.extensions,
      (i, e) => i.copy(extensions = e),
    )

    /**
     * A path item with no operations, servers, parameters or extensions, and
     * with `ref`, `summary` and `description` set to `None`.
     */
    val empty: PathItem = PathItem(
      ref = None,
      summary = None,
      description = None,
      get = None,
      put = None,
      post = None,
      delete = None,
      options = None,
      head = None,
      patch = None,
      trace = None,
      servers = List.empty,
      parameters = Set.empty,
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
   *   A Set of parameters that are applicable for this operation. If a
   *   parameter is already defined at the Path Item, the new definition will
   *   override it but can never remove it. The set MUST NOT include duplicated
   *   parameters. A unique parameter is defined by a combination of a name and
   *   location; `Parameter` equality compares only `name` and `in`, so the set
   *   holds at most one parameter per name and location. The set can use the
   *   Reference Object to link to parameters that are defined at the OpenAPI
   *   Object’s components/parameters.
   * @param requestBody
   *   The request body applicable for this operation. The requestBody is only
   *   supported in HTTP methods where the HTTP 1.1 specification [RFC7231] has
   *   explicitly defined semantics for request bodies. In other cases where the
   *   HTTP spec is vague, requestBody SHALL be ignored by consumers.
   * @param responses
   *   The possible responses as they are returned from executing this
   *   operation, keyed by status code or `default`.
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
   *   definition overrides any declared top-level security. An empty List is
   *   left out of the JSON, so the operation then has no `security` field.
   * @param servers
   *   An alternative server List to service this operation. If an alternative
   *   server object is specified at the Path Item Object or Root level, it will
   *   be overridden by this value.
   * @param extensions
   *   Extensions to the operation, written as `x-` fields of the Operation
   *   Object. The keys are stored without the `x-` prefix.
   */
  final case class Operation(
    tags: List[String] = List.empty,
    summary: Option[String],
    description: Option[Doc],
    externalDocs: Option[ExternalDoc],
    operationId: Option[String],
    parameters: Set[ReferenceOr[Parameter]] = Set.empty,
    requestBody: Option[ReferenceOr[RequestBody]],
    responses: Map[StatusOrDefault, ReferenceOr[Response]] = Map.empty,
    callbacks: Map[String, ReferenceOr[Callback]] = Map.empty,
    deprecated: Option[Boolean] = None,
    security: List[SecurityRequirement] = List.empty,
    servers: List[Server] = List.empty,
    @transientField
    @unroll
    extensions: Extensions = Extensions.empty,
  )

  /** The companion of `Operation`, with its schema. */
  object Operation extends OperationCompatibility {

    /**
     * The schema of `Operation`. It reads each `x-` field into `extensions`,
     * and writes `extensions` as `x-` fields sorted by key.
     */
    implicit val schema: Schema[Operation] = extensionSchema(
      DeriveSchema.gen[Operation],
      _.extensions,
      (i, e) => i.copy(extensions = e),
    )
  }

  /**
   * Describes a single operation parameter (the Parameter Object). Two
   * parameters are equal when they have the same `name` and `in`: `equals` and
   * `hashCode` ignore the other fields.
   *
   * @param name
   *   The name of the parameter. Parameter names are case sensitive.
   * @param in
   *   The location of the parameter. The Parameter Object allows `query`,
   *   `header`, `path` and `cookie`.
   * @param style
   *   How the parameter value is serialized, such as `form` or `simple`.
   * @param content
   *   A media type and the `MediaType` that describes the parameter, instead of
   *   `schema`.
   * @param extensions
   *   Extensions to the parameter, written as `x-` fields of the Parameter
   *   Object. The keys are stored without the `x-` prefix.
   */
  final case class Parameter(
    name: String,
    in: String,
    description: Option[Doc],
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    schema: Option[ReferenceOr[JsonSchema]],
    explode: Option[Boolean] = None,
    examples: Map[String, ReferenceOr[Example]] = Map.empty,
    allowReserved: Option[Boolean],
    style: Option[String],
    content: Option[(String, MediaType)],
    @transientField
    @unroll
    extensions: Extensions = Extensions.empty,
  ) {
    override def equals(obj: Any): Boolean = obj match {
      case p: Parameter if name == p.name && in == p.in => true
      case _                                            => false
    }

    override def hashCode(): Int = (name, in).hashCode
  }

  /**
   * The companion of `Parameter`, with its schema, the parameter styles, and
   * one constructor for each parameter location.
   */
  object Parameter {

    /**
     * The schema of `Parameter`. It reads each `x-` field into `extensions`,
     * and writes `extensions` as `x-` fields sorted by key.
     */
    implicit val schema: Schema[Parameter] = extensionSchema(
      DeriveSchema.gen[Parameter],
      _.extensions,
      (i, e) => i.copy(extensions = e),
    )

    /**
     * A media type name and its `MediaType`. `Parameter.content` holds the same
     * data as a `(String, MediaType)` tuple instead.
     */
    final case class Content(key: String, mediaType: MediaType)

    /**
     * A style that `pathParameter` accepts: `Style.Matrix`, `Style.Label` or
     * `Style.Simple`.
     */
    sealed trait PathStyle

    /**
     * A style that `queryParameter` accepts: `Style.Form`,
     * `Style.SpaceDelimited`, `Style.PipeDelimited` or `Style.DeepObject`.
     */
    sealed trait QueryStyle

    /**
     * The parameter styles of the Parameter Object, written in its `style`
     * field.
     */
    object Style {

      /** The `matrix` path style. */
      case object Matrix extends PathStyle

      /** The `label` path style. */
      case object Label extends PathStyle

      /** The `form` query style, the default of `queryParameter`. */
      case object Form extends QueryStyle

      /** The `simple` path style, the default of `pathParameter`. */
      case object Simple extends PathStyle

      /** The `spaceDelimited` query style. */
      case object SpaceDelimited extends QueryStyle

      /** The `pipeDelimited` query style. */
      case object PipeDelimited extends QueryStyle

      /** The `deepObject` query style. */
      case object DeepObject extends QueryStyle
    }

    /**
     * Parameters that are appended to the URL. For example, in /items?id=###,
     * the query parameter is id.
     *
     * The parameter written to JSON has `explode`, `required` and `deprecated`
     * only when they are true, and always has `allowReserved` and `style`.
     *
     * @param name
     *   The name of the parameter. Parameter names are case sensitive.
     * @param description
     *   A brief description of the parameter.
     * @param deprecated
     *   Specifies that a parameter is deprecated and SHOULD be transitioned out
     *   of usage.
     */
    def queryParameter(
      name: String,
      description: Option[Doc],
      schema: Option[ReferenceOr[JsonSchema]],
      examples: Map[String, ReferenceOr[Example]],
      deprecated: Boolean = false,
      explode: Boolean = true,
      required: Boolean = false,
      allowReserved: Boolean = false,
      style: QueryStyle = Style.Form,
      @unroll
      extensions: Extensions = Extensions.empty,
    ): Parameter = Parameter(
      name,
      "query",
      description,
      if (required) Some(required) else None,
      if (deprecated) Some(deprecated) else None,
      schema,
      if (explode) Some(explode) else None,
      examples,
      Some(allowReserved),
      style = Some(style match {
        case Style.Form           => "form"
        case Style.SpaceDelimited => "spaceDelimited"
        case Style.PipeDelimited  => "pipeDelimited"
        case Style.DeepObject     => "deepObject"
      }),
      None,
      extensions,
    )

    /**
     * Custom headers that are expected as part of the request. Note that
     * [RFC7230] states header names are case insensitive.
     *
     * The parameter has the `simple` style. The JSON has `required`,
     * `deprecated` and `explode` only when they are true.
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
     */
    def headerParameter(
      name: String,
      description: Option[Doc],
      required: Boolean,
      deprecated: Boolean = false,
      definition: Option[ReferenceOr[JsonSchema]] = None,
      explode: Boolean = false,
      examples: Map[String, ReferenceOr[Example]],
      @unroll
      extensions: Extensions = Extensions.empty,
    ): Parameter = Parameter(
      name,
      "header",
      description,
      if (required) Some(required) else None,
      if (deprecated) Some(deprecated) else None,
      definition,
      if (explode) Some(explode) else None,
      examples,
      allowReserved = None,
      style = Some("simple"),
      None,
      extensions,
    )

    /**
     * Used together with Path Templating, where the parameter value is actually
     * part of the operation’s URL. This does not include the host or base path
     * of the API. For example, in /items/{itemId}, the path parameter is
     * itemId.
     *
     * The parameter is always required. The JSON has `deprecated` and `explode`
     * only when they are true.
     *
     * @param name
     *   The name of the parameter. Parameter names are case sensitive.
     * @param description
     *   A brief description of the parameter.
     * @param deprecated
     *   Specifies that a parameter is deprecated and SHOULD be transitioned out
     *   of usage.
     */
    def pathParameter(
      name: String,
      description: Option[Doc],
      deprecated: Boolean = false,
      definition: Option[ReferenceOr[JsonSchema]] = None,
      style: PathStyle = Style.Simple,
      explode: Boolean = false,
      examples: Map[String, ReferenceOr[Example]],
      @unroll
      extensions: Extensions = Extensions.empty,
    ): Parameter = Parameter(
      name,
      "path",
      description,
      required = Some(true),
      if (deprecated) Some(deprecated) else None,
      definition,
      if (explode) Some(explode) else None,
      examples,
      allowReserved = None,
      style = Some(style match {
        case Style.Matrix => "matrix"
        case Style.Label  => "label"
        case Style.Simple => "simple"
      }),
      None,
      extensions,
    )

    /**
     * Used to pass a specific cookie value to the API.
     *
     * The parameter has the `form` style. The JSON has `required`, `deprecated`
     * and `explode` only when they are true.
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
     */
    def cookieParameter(
      name: String,
      description: Option[Doc],
      required: Boolean,
      deprecated: Boolean = false,
      definition: Option[ReferenceOr[JsonSchema]] = None,
      explode: Boolean = false,
      examples: Map[String, ReferenceOr[Example]],
    ): Parameter = Parameter(
      name,
      "cookie",
      description,
      if (required) Some(required) else None,
      if (deprecated) Some(deprecated) else None,
      definition,
      if (explode) Some(explode) else None,
      examples,
      allowReserved = None,
      style = Some("form"),
      None,
    )
  }

  /**
   * Describes a header of a response or of a multipart encoding (the Header
   * Object). A flag that is `None` is left out of the JSON.
   */
  final case class Header(
    description: Option[Doc],
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    allowEmptyValue: Option[Boolean] = None,
    schema: Option[JsonSchema],
  )

  /** The companion of `Header`, with its schema and a constructor. */
  object Header {

    /**
     * Creates a `Header` from plain flags. A flag that is `false` becomes
     * `None`, so it is left out of the JSON.
     */
    def apply(
      description: Option[Doc],
      required: Boolean,
      deprecated: Boolean,
      allowEmptyValue: Boolean,
      schema: Option[JsonSchema],
    ): Header = Header(
      description,
      if (required) Some(required) else None,
      if (deprecated) Some(deprecated) else None,
      if (allowEmptyValue) Some(allowEmptyValue) else None,
      schema,
    )

    /** The schema of `Header`, derived from its fields. */
    implicit val schema: Schema[Header] =
      DeriveSchema.gen[Header]
  }

  /**
   * Describes a single request body.
   *
   * @param description
   *   A brief description of the request body. This could contain examples of
   *   use.
   * @param content
   *   The content of the request body. The key is a media type or media type
   *   range and the value describes it. For requests that match multiple keys,
   *   only the most specific key is applicable.
   * @param required
   *   Determines if the request body is required in the request. The JSON has
   *   this field also when it is false.
   */
  final case class RequestBody(
    description: Option[Doc] = None,
    content: Map[String, MediaType] = Map.empty,
    required: Boolean = false,
  )

  /** The companion of `RequestBody`, with its schema. */
  object RequestBody {

    /** The schema of `RequestBody`, derived from its fields. */
    implicit val schema: Schema[RequestBody] =
      DeriveSchema.gen[RequestBody]
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
    schema: ReferenceOr[JsonSchema],
    examples: Map[String, ReferenceOr[Example]] = Map.empty,
    encoding: Map[String, Encoding] = Map.empty,
  )

  /** The companion of `MediaType`, with its schema. */
  object MediaType {

    /** The schema of `MediaType`, derived from its fields. */
    implicit val schema: Schema[MediaType] =
      DeriveSchema.gen[MediaType]
  }

  /**
   * A single encoding definition applied to a single schema property.
   *
   * `headers` defaults to an empty map, `style` to `form` and `allowReserved`
   * to false; `explode` has no default. The JSON always has `style`, `explode`
   * and `allowReserved`.
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
    headers: Map[String, ReferenceOr[Header]] = Map.empty,
    style: String = "form",
    explode: Boolean,
    allowReserved: Boolean = false,
  )

  /** The companion of `Encoding`, with its schema. */
  object Encoding {

    /** The schema of `Encoding`, derived from its fields. */
    implicit val schema: Schema[Encoding] =
      DeriveSchema.gen[Encoding]
  }

  /**
   * A container for the expected responses of an operation. The container maps
   * a HTTP response code to the expected response. The Responses Object MUST
   * contain at least one response code, and it SHOULD be the response for a
   * successful operation call.
   */
  type Responses = Map[StatusOrDefault, ReferenceOr[Response]]

  /**
   * A key of the responses map of an operation: one HTTP status (`StatusValue`)
   * or `Default`, the response for the status codes that have no entry of their
   * own.
   */
  sealed trait StatusOrDefault extends Product with Serializable {

    /**
     * The key as written in JSON: the status code, such as `200`, or `default`.
     */
    def text: String
  }

  /** The two kinds of `StatusOrDefault`: `StatusValue` and `Default`. */
  object StatusOrDefault {

    /** A response key for one HTTP status. */
    case class StatusValue(status: Status) extends StatusOrDefault {
      override def text: String = status.text
    }

    /** The companion of `StatusValue`, with its schema. */
    object StatusValue {

      /**
       * The schema of `StatusValue`: the status code as a JSON string, such as
       * `"200"`.
       */
      implicit val schema: Schema[StatusValue] =
        zio.schema
          .Schema[Status]
          .transformOrFail[StatusValue](
            s => Right(StatusValue(s)),
            p => Right(p.status),
          )
    }

    /** The `default` response key, for the status codes with no own entry. */
    case object Default extends StatusOrDefault {

      /**
       * The schema of `Default`: the JSON string `"default"`. Decoding another
       * string fails with `Invalid default status`.
       */
      implicit val schema: Schema[Default.type] =
        zio.schema
          .Schema[String]
          .transformOrFail[Default.type](
            s => if (s == "default") Right(Default) else Left("Invalid default status"),
            _ => Right("default"),
          )

      override def text: String = "default"
    }
  }

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
   *   a media type or media type range and the value describes it. For
   *   responses that match multiple keys, only the most specific key is
   *   applicable.
   * @param links
   *   A map of operations links that can be followed from the response. The key
   *   of the map is a short name for the link, following the naming constraints
   *   of the names for Component Objects.
   * @param extensions
   *   Extensions to the response, written as `x-` fields of the Response
   *   Object. The keys are stored without the `x-` prefix.
   */
  final case class Response(
    description: Option[Doc] = None,
    headers: Map[String, ReferenceOr[Header]] = Map.empty,
    content: Map[String, MediaType] = Map.empty,
    links: Map[String, ReferenceOr[Link]] = Map.empty,
    @transientField
    @unroll
    extensions: Extensions = Extensions.empty,
  )

  /** The companion of `Response`, with its schema. */
  object Response {

    /**
     * The schema of `Response`. It reads each `x-` field into `extensions`, and
     * writes `extensions` as `x-` fields sorted by key.
     */
    implicit val schema: Schema[Response] = extensionSchema(
      DeriveSchema.gen[Response],
      _.extensions,
      (i, e) => i.copy(extensions = e),
    )
  }

  /**
   * A map of possible out-of band callbacks related to the parent operation.
   * Each value in the map is a Path Item Object that describes a set of
   * requests that may be initiated by the API provider and the expected
   * responses. The key value used to identify the path item object is an
   * expression, evaluated at runtime, that identifies a URL to use for the
   * callback operation.
   *
   * The JSON form is an object with a single `expressions` field that holds
   * this map.
   *
   * @param expressions
   *   Maps each runtime expression to the Path Item Object that defines the
   *   callback request and the expected responses.
   */
  final case class Callback(expressions: Map[String, PathItem] = Map.empty)

  /** The companion of `Callback`, with its schema. */
  object Callback {

    /** The schema of `Callback`, derived from its fields. */
    implicit val schema: Schema[Callback] =
      DeriveSchema.gen[Callback]
  }

  // There is currently no API to set the summary, description or externalValue
  /**
   * An example value (the Example Object). In all cases, the example value is
   * expected to be compatible with the type schema of its associated value.
   * Tooling implementations MAY choose to validate compatibility automatically,
   * and reject the example value(s) if incompatible.
   *
   * @param value
   *   The example value, as JSON.
   * @param summary
   *   Short description for the example.
   * @param description
   *   Long description for the example.
   * @param externalValue
   *   A URL that points to the literal example. This provides the capability to
   *   reference examples that cannot easily be included in JSON or YAML
   *   documents.
   */
  final case class Example(
    value: Json,
    summary: Option[String] = None,
    description: Option[Doc] = None,
    externalValue: Option[URI] = None,
  )

  /** The companion of `Example`, with its schema. */
  object Example {

    /** The schema of `Example`, derived from its fields. */
    implicit val schema: Schema[Example] =
      DeriveSchema.gen[Example]
  }

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
    parameters: Map[String, LiteralOrExpression] = Map.empty,
    requestBody: LiteralOrExpression,
    description: Option[Doc],
    server: Option[Server],
  )

  /** The companion of `Link`, with its schema. */
  object Link {

    /** The schema of `Link`, derived from its fields. */
    implicit val schema: Schema[Link] =
      DeriveSchema.gen[Link]
  }

  /**
   * A value of a Link Object: a literal number, decimal, string or boolean, or
   * a runtime expression.
   */
  sealed trait LiteralOrExpression

  /** The cases of `LiteralOrExpression`, and the `expression` constructor. */
  object LiteralOrExpression {

    /**
     * The schema of `LiteralOrExpression`. It writes a value as an object with
     * one field named after its case, such as `{"NumberLiteral":5}`.
     */
    implicit val schema: Schema[LiteralOrExpression] =
      DeriveSchema.gen[LiteralOrExpression]

    /** A whole-number literal. */
    final case class NumberLiteral(value: Long) extends LiteralOrExpression

    /** The companion of `NumberLiteral`, with its schema. */
    object NumberLiteral {

      /** The schema of `NumberLiteral`, as a plain JSON number. */
      implicit val schema: Schema[NumberLiteral] =
        Schema[Long].transform[NumberLiteral](s => NumberLiteral(s), p => p.value)
    }

    /** A decimal number literal. */
    final case class DecimalLiteral(value: Double) extends LiteralOrExpression

    /** The companion of `DecimalLiteral`, with its schema. */
    object DecimalLiteral {

      /** The schema of `DecimalLiteral`, as a plain JSON number. */
      implicit val schema: Schema[DecimalLiteral] =
        Schema[Double].transform[DecimalLiteral](s => DecimalLiteral(s), p => p.value)
    }

    /** A string literal. */
    final case class StringLiteral(value: String) extends LiteralOrExpression

    /** The companion of `StringLiteral`, with its schema. */
    object StringLiteral {

      /** The schema of `StringLiteral`, as a plain JSON string. */
      implicit val schema: Schema[StringLiteral] =
        Schema[String].transform[StringLiteral](s => StringLiteral(s), p => p.value)
    }

    /** A boolean literal. */
    final case class BooleanLiteral(value: Boolean) extends LiteralOrExpression

    /** The companion of `BooleanLiteral`, with its schema. */
    object BooleanLiteral {

      /** The schema of `BooleanLiteral`, as a plain JSON boolean. */
      implicit val schema: Schema[BooleanLiteral] =
        Schema[Boolean].transform[BooleanLiteral](s => BooleanLiteral(s), p => p.value)
    }

    /** A runtime expression, such as `\$request.path.id`. */
    case class Expression(value: String) extends LiteralOrExpression

    /** The companion of `Expression`, with its schema. */
    object Expression {

      /** The schema of `Expression`, as a plain JSON string. */
      implicit val schema: Schema[Expression] =
        Schema[String].transform[Expression](s => Expression.create(s), p => p.value)

      private[openapi] def create(value: String): Expression = Expression(value)
    }

    // TODO: maybe one could make a regex to validate the expression. For now just accept anything
    // https://swagger.io/specification/#runtime-expressions
    /**
     * The pattern that `expression` accepts: `.*`, which matches the empty
     * string and any string without a line break.
     */
    val ExpressionRegex: Regex = """.*""".r

    /**
     * Returns an `Expression` for `value` when it matches `ExpressionRegex`,
     * and `None` otherwise, for example for a string that contains a newline.
     */
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
   * @param extensions
   *   Extensions to the tag, written as `x-` fields of the Tag Object. The keys
   *   are stored without the `x-` prefix.
   */
  final case class Tag(
    name: String,
    description: Option[Doc],
    externalDocs: Option[ExternalDoc],
    @transientField
    @unroll
    extensions: Extensions = Extensions.empty,
  )

  /** The companion of `Tag`, with its schema. */
  object Tag extends TagCompatibility {

    /**
     * The schema of `Tag`. It reads each `x-` field into `extensions`, and
     * writes `extensions` as `x-` fields sorted by key.
     */
    implicit val schema: Schema[Tag] = extensionSchema(
      DeriveSchema.gen[Tag],
      _.extensions,
      (i, e) => i.copy(extensions = e),
    )
  }

  /**
   * Either a reference to a component (`Reference`, the Reference Object) or an
   * inline value (`Or`). The JSON has no discriminator: a reference is an
   * object with a `\$ref` field, and an inline value is written as the value
   * itself.
   */
  @noDiscriminator
  sealed trait ReferenceOr[+T] {

    /**
     * Returns this value as a `JsonSchema`. An inline schema is returned as is.
     * A reference becomes a `\$ref` schema whose description is the reference's
     * summary followed by its description, as CommonMark, and is empty when
     * both are `None`.
     */
    def asJsonSchema(implicit ev: T <:< JsonSchema): JsonSchema = this match {
      case ReferenceOr.Reference(ref, summary, description) =>
        JsonSchema
          .RefSchema(ref)
          .description((summary.getOrElse(Doc.empty) + description.getOrElse(Doc.empty)).toCommonMark)
      case ReferenceOr.Or(value)                            => ev(value)
    }

  }

  /** The two cases of `ReferenceOr`: `Reference` and `Or`. */
  object ReferenceOr {

    /**
     * The schema of `ReferenceOr[T]`, built from the schema of `T`. It decodes
     * an object with a `\$ref` field as a `Reference`, and other input as an
     * inline `T`.
     */
    implicit def schema[T: Schema]: Schema[ReferenceOr[T]] =
      DeriveSchema.gen[ReferenceOr[T]]

    /**
     * A simple object to allow referencing other components in the
     * specification, internally and externally.
     *
     * @param ref
     *   The reference string, written as the `\$ref` field.
     * @param summary
     *   A short summary of the referenced component.
     * @param description
     *   A description of the referenced component.
     */
    @nowarn("msg=possible missing interpolator")
    final case class Reference(
      @fieldName("$ref") ref: String,
      summary: Option[Doc] = None,
      description: Option[Doc] = None,
    ) extends ReferenceOr[Nothing]

    /** The companion of `Reference`, with its schema. */
    object Reference {

      /** The schema of `Reference`, derived from its fields. */
      implicit val schema: Schema[Reference] =
        DeriveSchema.gen[Reference]
    }

    /** An inline value, used instead of a reference. */
    final case class Or[T](value: T) extends ReferenceOr[T]

    /** The companion of `Or`, with its schema. */
    object Or {

      /** The schema of `Or[T]`: the value as `T`, with no wrapper object. */
      implicit def schema[T: Schema]: Schema[Or[T]] =
        Schema[T].transform(Or(_), _.value)

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
  final case class Discriminator(
    propertyName: String,
    mapping: Map[String, String] = Map.empty,
  )

  /** The companion of `Discriminator`, with its schema. */
  object Discriminator {

    /** The schema of `Discriminator`, derived from its fields. */
    implicit val schema: Schema[Discriminator] =
      DeriveSchema.gen[Discriminator]
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
   *   wrapped (for example, `<books><book/><book/></books>`) or unwrapped
   *   (`<book/><book/>`). The definition takes effect only when defined
   *   alongside type being array (outside the items).
   */
  final case class XML(name: String, namespace: URI, prefix: String, attribute: Boolean = false, wrapped: Boolean)

  /**
   * A security scheme declared in the document (the Security Scheme Object): an
   * API key (`ApiKey`), HTTP authentication (`Http`), OAuth 2 (`OAuth2`) or
   * OpenID Connect (`OpenIdConnect`).
   */
  @discriminatorName("type")
  sealed trait SecurityScheme {

    /** A short description of the security scheme. */
    def description: Option[Doc]
  }

  /** The cases of `SecurityScheme`, and the types they use. */
  object SecurityScheme {

    /**
     * The schema of `SecurityScheme`. It writes the case in a `type` field:
     * `apiKey`, `http`, `oauth2` or `openIdConnect`.
     */
    implicit val schema: Schema[SecurityScheme] =
      DeriveSchema.gen[SecurityScheme]

    /**
     * An API key security scheme (`type` `apiKey`), with the name and location
     * of the key.
     *
     * @param description
     *   A short description for security scheme.
     * @param name
     *   The name of the header, query or cookie parameter to be used.
     * @param in
     *   The location of the API key.
     */
    @caseName("apiKey")
    final case class ApiKey(description: Option[Doc], name: String, in: ApiKey.In) extends SecurityScheme

    /** The companion of `ApiKey`, with the key locations `In`. */
    object ApiKey {

      /** The location of an API key: `Query`, `Header` or `Cookie`. */
      sealed trait In extends Product with Serializable

      /** The three locations of an API key. */
      object In {

        /**
         * The schema of `In`: the location as `query`, `header` or `cookie`.
         * Decoding ignores case, so `HEADER` reads as `Header`. Another string
         * fails, for example with `Invalid ApiKey.In body` for `body`.
         */
        implicit val schema: Schema[In] =
          Schema[String]
            .transformOrFail(
              s =>
                s.toLowerCase match {
                  case "query"  => Right(Query)
                  case "header" => Right(Header)
                  case "cookie" => Right(Cookie)
                  case _        => Left(s"Invalid ApiKey.In $s")
                },
              {
                case Query  => Right("query")
                case Header => Right("header")
                case Cookie => Right("cookie")
              },
            )

        /** A query parameter, written as `query`. */
        case object Query extends In

        /** A request header, written as `header`. */
        case object Header extends In

        /** A cookie, written as `cookie`. */
        case object Cookie extends In
      }
    }

    /**
     * An HTTP authentication security scheme (`type` `http`), such as basic or
     * bearer authentication.
     *
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
    @caseName("http")
    final case class Http(description: Option[Doc], scheme: String, bearerFormat: Option[String]) extends SecurityScheme

    /**
     * An OAuth 2 security scheme (`type` `oauth2`), with its flows.
     *
     * @param description
     *   A short description for security scheme.
     * @param flows
     *   An object containing configuration information for the flow types
     *   supported.
     */
    @caseName("oauth2")
    final case class OAuth2(description: Option[Doc], flows: OAuthFlows) extends SecurityScheme

    /**
     * An OpenID Connect security scheme (`type` `openIdConnect`).
     *
     * @param description
     *   A short description for security scheme.
     * @param openIdConnectUrl
     *   OpenId Connect URL to discover OAuth2 configuration values.
     */
    @caseName("openIdConnect")
    final case class OpenIdConnect(description: Option[Doc], openIdConnectUrl: URI) extends SecurityScheme

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
    )

    /**
     * The settings shared by the OAuth flows of `OAuthFlows`: an optional
     * refresh URL and the available scopes.
     */
    sealed trait OAuthFlow {

      /** The URL to be used for obtaining refresh tokens, if any. */
      def refreshUrl: Option[URI]

      /**
       * The available scopes, as a map from each scope name to a short
       * description. The map may be empty.
       */
      def scopes: Map[String, String]
    }

    /**
     * The four OAuth flows: `Implicit`, `AuthorizationCode`, `Password` and
     * `ClientCredentials`.
     */
    object OAuthFlow {

      /**
       * Configuration for the OAuth Implicit flow.
       *
       * @param authorizationUrl
       *   The authorization URL to be used for this flow.
       * @param refreshUrl
       *   The URL to be used for obtaining refresh tokens.
       * @param scopes
       *   The available scopes for the OAuth2 security scheme. A map between
       *   the scope name and a short description for it. The map MAY be empty.
       */
      final case class Implicit(authorizationUrl: URI, refreshUrl: Option[URI], scopes: Map[String, String])
          extends OAuthFlow

      /**
       * Configuration for the OAuth Authorization Code flow. Previously called
       * accessCode in OpenAPI 2.0.
       *
       * @param authorizationUrl
       *   The authorization URL to be used for this flow.
       * @param refreshUrl
       *   The URL to be used for obtaining refresh tokens.
       * @param scopes
       *   The available scopes for the OAuth2 security scheme. A map between
       *   the scope name and a short description for it. The map MAY be empty.
       * @param tokenUrl
       *   The token URL to be used for this flow.
       */
      final case class AuthorizationCode(
        authorizationUrl: URI,
        refreshUrl: Option[URI],
        scopes: Map[String, String],
        tokenUrl: URI,
      ) extends OAuthFlow

      /**
       * Configuration for the OAuth Resource Owner Password flow.
       *
       * @param refreshUrl
       *   The URL to be used for obtaining refresh tokens.
       * @param scopes
       *   The available scopes for the OAuth2 security scheme. A map between
       *   the scope name and a short description for it. The map MAY be empty.
       * @param tokenUrl
       *   The token URL to be used for this flow.
       */
      final case class Password(refreshUrl: Option[URI], scopes: Map[String, String], tokenUrl: URI) extends OAuthFlow

      /**
       * Configuration for the OAuth Client Credentials flow. Previously called
       * application in OpenAPI 2.0.
       *
       * @param refreshUrl
       *   The URL to be used for obtaining refresh tokens.
       * @param scopes
       *   The available scopes for the OAuth2 security scheme. A map between
       *   the scope name and a short description for it. The map MAY be empty.
       * @param tokenUrl
       *   The token URL to be used for this flow.
       */
      final case class ClientCredentials(refreshUrl: Option[URI], scopes: Map[String, String], tokenUrl: URI)
          extends OAuthFlow {}
    }

    /**
     * Lists the required security schemes to execute this operation. The name
     * used for each property MUST correspond to a security scheme declared in
     * the Security Schemes under the Components Object.
     *
     * Security Requirement Objects that contain multiple schemes require that
     * all schemes MUST be satisfied for a request to be authorized. This
     * enables support for scenarios where multiple query parameters or HTTP
     * headers are required to convey security information.
     *
     * When a list of Security Requirement Objects is defined on the OpenAPI
     * Object or Operation Object, only one of the Security Requirement Objects
     * in the list needs to be satisfied to authorize the request.
     *
     * @param securitySchemes
     *   If the security scheme is of type "oauth2" or "openIdConnect", then the
     *   value is a list of scope names required for the execution, and the list
     *   MAY be empty if authorization does not require a specified scope.
     *   OpenAPI 3.1 supports the use of scopes even on Non-OAuth2 security
     *   schemes.
     */
    final case class SecurityRequirement(securitySchemes: Map[String, List[String]])
  }

  /**
   * Specification extensions of an object, keyed without the `x-` prefix. The
   * schemas of `Info`, `PathItem`, `Operation`, `Parameter`, `Response` and
   * `Tag` write each entry as a field named `x-` followed by the key.
   */
  final case class Extensions(underlying: Map[String, Json]) {

    /**
     * Combines two sets of extensions. For a key in both, the value of `other`
     * is kept.
     */
    def ++(other: Extensions): Extensions = Extensions(underlying ++ other.underlying)
  }

  /** The companion of `Extensions`, with `empty`. */
  object Extensions {

    /** No extensions. */
    val empty = Extensions(Map.empty)
  }
}
