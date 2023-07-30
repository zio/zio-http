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

import zio.Chunk
import zio.json._
import zio.json.ast.Json
import zio.json.internal.Write
import zio.json.yaml._

final case class OpenAPIObject(
  openapi: String,
  info: InfoObject,
  servers: Option[Chunk[ServerObject]],
  paths: Map[String, PathItemObject],
  components: Option[ComponentsObject],
)

object OpenAPIObject {
  implicit lazy val codec: JsonCodec[OpenAPIObject] = DeriveJsonCodec.gen
}

final case class ServerObject(
  url: String,
  description: Option[String],
  variables: Option[Map[String, ServerVariableObject]],
)

object ServerObject {
  implicit lazy val codec: JsonCodec[ServerObject] = DeriveJsonCodec.gen
}

final case class ServerVariableObject(
  `enum`: Option[Chunk[String]],
  default: String,
  description: Option[String],
)

object ServerVariableObject {
  implicit lazy val codec: JsonCodec[ServerVariableObject] = DeriveJsonCodec.gen
}

final case class InfoObject(
  title: String,
  description: Option[String],
  termsOfService: Option[String],
  contact: Option[ContactObject],
  license: Option[LicenseObject],
  version: String,
)

object InfoObject {
  implicit lazy val codec: JsonCodec[InfoObject] = DeriveJsonCodec.gen
}

final case class ContactObject(
  name: Option[String],
  url: Option[String],
  email: Option[String],
)

object ContactObject {
  implicit lazy val codec: JsonCodec[ContactObject] = DeriveJsonCodec.gen
}

final case class LicenseObject(
  name: String,
  url: Option[String],
)

object LicenseObject {
  implicit lazy val codec: JsonCodec[LicenseObject] = DeriveJsonCodec.gen
}

sealed trait ReferenceOr[A] extends Product with Serializable

object ReferenceOr {
  final case class Reference[A](ref: ReferenceObject) extends ReferenceOr[A]
  final case class Value[A](value: A)                 extends ReferenceOr[A]

  private def encoder[A](implicit aCodec: JsonCodec[A]): JsonEncoder[ReferenceOr[A]] =
    new JsonEncoder[ReferenceOr[A]] {
      override def unsafeEncode(a: ReferenceOr[A], indent: Option[Int], out: Write): Unit =
        a match {
          case Reference(ref) => ReferenceObject.codec.encoder.unsafeEncode(ref, indent, out)
          case Value(value)   => aCodec.encoder.unsafeEncode(value, indent, out)
        }
    }

  private def decoder[A](implicit aCodec: JsonCodec[A]): JsonDecoder[ReferenceOr[A]] =
    ReferenceObject.codec.decoder
      .map(Reference[A](_))
      .orElse(aCodec.decoder.map(Value(_)))

  implicit def codec[A](implicit aCodec: JsonCodec[A]): JsonCodec[ReferenceOr[A]] =
    JsonCodec(encoder, decoder)
}

final case class ComponentsObject(
  schemas: Option[Map[String, ReferenceOr[SchemaObject]]],
  responses: Option[Map[String, ReferenceOr[ResponseObject]]],
  parameters: Option[Map[String, ReferenceOr[ParameterObject]]],
  examples: Option[Map[String, ReferenceOr[ExampleObject]]],
  requestBodies: Option[Map[String, ReferenceOr[RequestBodyObject]]],
  headers: Option[Map[String, ReferenceOr[HeaderObject]]],
  links: Option[Map[String, ReferenceOr[LinkObject]]],
)

object ComponentsObject {
  implicit lazy val codec: JsonCodec[ComponentsObject] = DeriveJsonCodec.gen
}

final case class PathItemObject(
  $ref: Option[String],
  summary: Option[String],
  description: Option[String],
  get: Option[OperationObject],
  put: Option[OperationObject],
  post: Option[OperationObject],
  delete: Option[OperationObject],
  options: Option[OperationObject],
  head: Option[OperationObject],
  patch: Option[OperationObject],
  trace: Option[OperationObject],
  servers: Option[Chunk[ServerObject]],
  parameters: Option[Chunk[ParameterObject]],
)

object PathItemObject {
  implicit lazy val codec: JsonCodec[PathItemObject] = DeriveJsonCodec.gen
}

final case class OperationObject(
  tags: Option[Chunk[String]],
  summary: Option[String],
  description: Option[String],
  externalDocs: Option[ExternalDocumentationObject],
  operationId: Option[String],
  parameters: Option[Chunk[ParameterObject]],
  requestBody: Option[ReferenceOr[RequestBodyObject]],
  responses: Map[String, ResponseObject],
  deprecated: Option[Boolean],
  servers: Option[Chunk[ServerObject]],
)

object OperationObject {
  implicit lazy val codec: JsonCodec[OperationObject] = DeriveJsonCodec.gen
}

final case class ExternalDocumentationObject(
  description: Option[String],
  url: String,
)

object ExternalDocumentationObject {
  implicit lazy val codec: JsonCodec[ExternalDocumentationObject] = DeriveJsonCodec.gen
}

sealed trait ParameterLocation extends Product with Serializable

object ParameterLocation {
  case object Query  extends ParameterLocation
  case object Header extends ParameterLocation
  case object Path   extends ParameterLocation
  case object Cookie extends ParameterLocation

  implicit lazy val codec: JsonCodec[ParameterLocation] =
    JsonCodec.string.transformOrFail(
      {
        case "query"  => Right(Query)
        case "header" => Right(Header)
        case "path"   => Right(Path)
        case "cookie" => Right(Cookie)
        case _        => Left("Invalid parameter location")
      },
      {
        case Query  => "query"
        case Header => "header"
        case Path   => "path"
        case Cookie => "cookie"
      },
    )

}

sealed trait ParameterStyle extends Product with Serializable

object ParameterStyle {
  case object Matrix         extends ParameterStyle
  case object Label          extends ParameterStyle
  case object Form           extends ParameterStyle
  case object Simple         extends ParameterStyle
  case object SpaceDelimited extends ParameterStyle
  case object PipeDelimited  extends ParameterStyle
  case object DeepObject     extends ParameterStyle

  implicit lazy val codec: JsonCodec[ParameterStyle] =
    JsonCodec.string.transformOrFail(
      {
        case "matrix"          => Right(Matrix)
        case "label"           => Right(Label)
        case "form"            => Right(Form)
        case "simple"          => Right(Simple)
        case "space_delimited" => Right(SpaceDelimited)
        case "pipe_delimited"  => Right(PipeDelimited)
        case "deep_object"     => Right(DeepObject)
        case _                 => Left("Invalid parameter style")
      },
      {
        case Matrix         => "matrix"
        case Label          => "label"
        case Form           => "form"
        case Simple         => "simple"
        case SpaceDelimited => "space_delimited"
        case PipeDelimited  => "pipe_delimited"
        case DeepObject     => "deep_object"
      },
    )
}

final case class ParameterObject(
  name: String,
  in: ParameterLocation,
  description: Option[String],
  required: Option[Boolean],
  deprecated: Option[Boolean],
  allowEmptyValue: Option[Boolean],
  style: Option[ParameterStyle],
  explode: Option[Boolean],
  allowReserved: Option[Boolean],
  schema: ReferenceOr[SchemaObject],
  examples: Option[Map[String, ReferenceOr[ExampleObject]]],
  example: Option[Json],
  content: Option[Map[String, MediaTypeObject]],
)

object ParameterObject {
  implicit lazy val codec: JsonCodec[ParameterObject] = DeriveJsonCodec.gen
}

final case class RequestBodyObject(
  description: Option[String],
  content: Map[String, MediaTypeObject],
  required: Option[Boolean],
)

object RequestBodyObject {
  implicit lazy val codec: JsonCodec[RequestBodyObject] = DeriveJsonCodec.gen
}

final case class MediaTypeObject(
  schema: ReferenceOr[SchemaObject],
  examples: Option[Map[String, ReferenceOr[ExampleObject]]],
  example: Option[Json],
)

object MediaTypeObject {
  implicit lazy val codec: JsonCodec[MediaTypeObject] = DeriveJsonCodec.gen
}

final case class ResponseObject(
  description: String,
  headers: Option[Map[String, ReferenceOr[HeaderObject]]],
  content: Option[Map[String, MediaTypeObject]],
  links: Option[Map[String, ReferenceOr[LinkObject]]],
)

object ResponseObject {
  implicit lazy val codec: JsonCodec[ResponseObject] = DeriveJsonCodec.gen
}

final case class ExampleObject(
  summary: Option[String],
  description: Option[String],
  value: Option[Json],
  externalValue: Option[String],
)

object ExampleObject {
  implicit lazy val codec: JsonCodec[ExampleObject] = DeriveJsonCodec.gen
}

final case class LinkObject(
  operationRef: Option[String],
  operationId: Option[String],
  parameters: Option[Map[String, Json]],
  requestBody: Option[Either[Json, String]],
  description: Option[String],
  server: Option[ServerObject],
)

object LinkObject {
  implicit lazy val codec: JsonCodec[LinkObject] = DeriveJsonCodec.gen
}

final case class HeaderObject(
  description: Option[String],
  required: Option[Boolean],
  deprecated: Option[Boolean],
  allowEmptyValue: Option[Boolean],
  style: Option[String],
  explode: Option[Boolean],
  allowReserved: Option[Boolean],
  schema: Option[ReferenceOr[SchemaObject]],
  example: Option[Json],
  examples: Option[Map[String, ReferenceOr[ExampleObject]]],
  content: Option[Map[String, MediaTypeObject]],
)

object HeaderObject {
  implicit lazy val codec: JsonCodec[HeaderObject] = DeriveJsonCodec.gen
}

final case class TagObject(
  name: String,
  description: Option[String],
  externalDocs: Option[ExternalDocumentationObject],
)

object TagObject {
  implicit lazy val codec: JsonCodec[TagObject] = DeriveJsonCodec.gen
}

final case class ReferenceObject(
  $ref: String,
)

object ReferenceObject {
  implicit lazy val codec: JsonCodec[ReferenceObject] = DeriveJsonCodec.gen
}

sealed trait SchemaObjectType extends Product with Serializable

object SchemaObjectType {
  case object Integer extends SchemaObjectType
  case object Number  extends SchemaObjectType
  case object String  extends SchemaObjectType
  case object Boolean extends SchemaObjectType
  case object Object  extends SchemaObjectType
  case object Null    extends SchemaObjectType
  case object Array   extends SchemaObjectType

  implicit lazy val codec: JsonCodec[SchemaObjectType] =
    JsonCodec.string.transformOrFail(
      {
        case "integer" => Right(Integer)
        case "number"  => Right(Number)
        case "string"  => Right(String)
        case "boolean" => Right(Boolean)
        case "object"  => Right(Object)
        case "null"    => Right(Null)
        case "array"   => Right(Array)
        case _         => Left("Invalid schema type")
      },
      {
        case Integer => "integer"
        case Number  => "number"
        case String  => "string"
        case Boolean => "boolean"
        case Object  => "object"
        case Null    => "null"
        case Array   => "array"
      },
    )
}

sealed trait SchemaObjectFormat extends Product with Serializable

object SchemaObjectFormat {
  case object Int32               extends SchemaObjectFormat
  case object Int64               extends SchemaObjectFormat
  case object Float               extends SchemaObjectFormat
  case object Double              extends SchemaObjectFormat
  case object Byte                extends SchemaObjectFormat
  case object Binary              extends SchemaObjectFormat
  case object Date                extends SchemaObjectFormat
  case object DateTime            extends SchemaObjectFormat
  case object Password            extends SchemaObjectFormat
  case class Other(value: String) extends SchemaObjectFormat

  implicit lazy val codec: JsonCodec[SchemaObjectFormat] =
    JsonCodec.string.transformOrFail(
      {
        case "int32"     => Right(Int32)
        case "int64"     => Right(Int64)
        case "float"     => Right(Float)
        case "double"    => Right(Double)
        case "byte"      => Right(Byte)
        case "binary"    => Right(Binary)
        case "date"      => Right(Date)
        case "date-time" => Right(DateTime)
        case "password"  => Right(Password)
        case other       => Right(Other(other))
      },
      {
        case Int32        => "int32"
        case Int64        => "int64"
        case Float        => "float"
        case Double       => "double"
        case Byte         => "byte"
        case Binary       => "binary"
        case Date         => "date"
        case DateTime     => "date-time"
        case Password     => "password"
        case Other(value) => value
      },
    )
}

final case class SchemaObject(
  nullable: Option[Boolean],
  discriminator: Option[DiscriminatorObject],
  readOnly: Option[Boolean],
  writeOnly: Option[Boolean],
  xml: Option[XmlObject],
  externalDocs: Option[ExternalDocumentationObject],
  example: Option[Json],
  examples: Option[Chunk[Json]],
  deprecated: Option[Boolean],
  `type`: Option[SchemaObjectType],
  format: Option[SchemaObjectFormat],
  allOf: Option[Chunk[ReferenceOr[SchemaObject]]],
  oneOf: Option[Chunk[ReferenceOr[SchemaObject]]],
  anyOf: Option[Chunk[ReferenceOr[SchemaObject]]],
  not: Option[ReferenceOr[SchemaObject]],
  items: Option[ReferenceOr[SchemaObject]],
  properties: Option[Map[String, ReferenceOr[SchemaObject]]],
  additionalProperties: Option[ReferenceOr[SchemaObject]],
  description: Option[String],
  default: Option[Json],
  title: Option[String],
  required: Option[Chunk[String]],
  `enum`: Option[Chunk[Json]],
)

object SchemaObject {
  implicit lazy val codec: JsonCodec[SchemaObject] = DeriveJsonCodec.gen
}

final case class DiscriminatorObject(propertyName: String, mapping: Option[Map[String, String]])

object DiscriminatorObject {
  implicit lazy val codec: JsonCodec[DiscriminatorObject] = DeriveJsonCodec.gen
}

final case class XmlObject(
  name: Option[String],
  namespace: Option[String],
  prefix: Option[String],
  attribute: Option[Boolean],
  wrapped: Option[Boolean],
)

object XmlObject {
  implicit lazy val codec: JsonCodec[XmlObject] = DeriveJsonCodec.gen
}
