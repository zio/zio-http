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

package zio.http.codec
import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.schema.Schema
import zio.schema.annotation.simpleEnum

import zio.http.codec.internal.TextBinaryCodec

private[codec] trait QueryCodecs {

  def query[A](name: String)(implicit schema: Schema[A]): QueryCodec[A] =
    schema match {
      case s @ Schema.Primitive(_, _)                                                    =>
        HttpCodec.Query(
          HttpCodec.Query.QueryType
            .Primitive(name, BinaryCodecWithSchema.fromBinaryCodec(TextBinaryCodec.fromSchema(s))(s)),
        )
      case c @ Schema.Sequence(elementSchema, _, _, _, _)                                =>
        if (supportedElementSchema(elementSchema.asInstanceOf[Schema[Any]])) {
          HttpCodec.Query(
            HttpCodec.Query.QueryType.Collection(
              c,
              HttpCodec.Query.QueryType.Primitive(
                name,
                BinaryCodecWithSchema(TextBinaryCodec.fromSchema(elementSchema), elementSchema),
              ),
              optional = false,
            ),
          )
        } else {
          throw new IllegalArgumentException("Only primitive types can be elements of sequences")
        }
      case c @ Schema.Set(elementSchema, _)                                              =>
        if (supportedElementSchema(elementSchema.asInstanceOf[Schema[Any]])) {
          HttpCodec.Query(
            HttpCodec.Query.QueryType.Collection(
              c,
              HttpCodec.Query.QueryType.Primitive(
                name,
                BinaryCodecWithSchema(TextBinaryCodec.fromSchema(elementSchema), elementSchema),
              ),
              optional = false,
            ),
          )
        } else {
          throw new IllegalArgumentException("Only primitive types can be elements of sets")
        }
      case Schema.Optional(Schema.Primitive(_, _), _)                                    =>
        HttpCodec.Query(
          HttpCodec.Query.QueryType
            .Primitive(name, BinaryCodecWithSchema.fromBinaryCodec(TextBinaryCodec.fromSchema(schema))(schema)),
        )
      case Schema.Optional(c @ Schema.Sequence(elementSchema, _, _, _, _), _)            =>
        if (supportedElementSchema(elementSchema.asInstanceOf[Schema[Any]])) {
          HttpCodec.Query(
            HttpCodec.Query.QueryType.Collection(
              c,
              HttpCodec.Query.QueryType.Primitive(
                name,
                BinaryCodecWithSchema(TextBinaryCodec.fromSchema(elementSchema), elementSchema),
              ),
              optional = true,
            ),
          )
        } else {
          throw new IllegalArgumentException("Only primitive types can be elements of sequences")
        }
      case Schema.Optional(inner, _) if inner.isInstanceOf[Schema.Set[_]]                =>
        val elementSchema = inner.asInstanceOf[Schema.Set[Any]].elementSchema
        if (supportedElementSchema(elementSchema)) {
          HttpCodec.Query(
            HttpCodec.Query.QueryType.Collection(
              inner.asInstanceOf[Schema.Set[_]],
              HttpCodec.Query.QueryType.Primitive(
                name,
                BinaryCodecWithSchema(TextBinaryCodec.fromSchema(inner), inner),
              ),
              optional = true,
            ),
          )
        } else {
          throw new IllegalArgumentException("Only primitive types can be elements of sets")
        }
      case enum0: Schema.Enum[_] if enum0.annotations.exists(_.isInstanceOf[simpleEnum]) =>
        HttpCodec.Query(
          HttpCodec.Query.QueryType
            .Primitive(name, BinaryCodecWithSchema.fromBinaryCodec(TextBinaryCodec.fromSchema(schema))(schema)),
        )
      case record: Schema.Record[A] if record.fields.size == 1                           =>
        val field = record.fields.head
        if (supportedElementSchema(field.schema.asInstanceOf[Schema[Any]])) {
          HttpCodec.Query(
            HttpCodec.Query.QueryType.Primitive(
              name,
              BinaryCodecWithSchema(TextBinaryCodec.fromSchema(record), record),
            ),
          )
        } else {
          throw new IllegalArgumentException("Only primitive types can be elements of records")
        }
      case other                                                                         =>
        throw new IllegalArgumentException(
          s"Only primitive types, sequences, sets, optional, enums and records with a single field can be used to infer query codecs, but got $other",
        )
    }

  private def supportedElementSchema(elementSchema: Schema[Any]) =
    elementSchema.isInstanceOf[Schema.Primitive[_]] ||
      elementSchema.isInstanceOf[Schema.Enum[_]] && elementSchema.annotations.exists(_.isInstanceOf[simpleEnum]) ||
      elementSchema.isInstanceOf[Schema.Record[_]] && elementSchema.asInstanceOf[Schema.Record[_]].fields.size == 1

  def queryAll[A](implicit schema: Schema[A]): QueryCodec[A] =
    schema match {
      case _: Schema.Primitive[A]   =>
        throw new IllegalArgumentException("Use query[A](name: String) for primitive types")
      case record: Schema.Record[A] => HttpCodec.Query(HttpCodec.Query.QueryType.Record(record))
      case Schema.Optional(_, _)    => HttpCodec.Query(HttpCodec.Query.QueryType.Record(schema))
      case _ => throw new IllegalArgumentException("Only case classes can be used to infer query codecs")
    }

}
