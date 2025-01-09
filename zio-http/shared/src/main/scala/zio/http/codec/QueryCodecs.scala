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
import scala.annotation.tailrec

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.schema.Schema
import zio.schema.annotation.simpleEnum

private[codec] trait QueryCodecs {

  def query[A](name: String)(implicit schema: Schema[A]): QueryCodec[A] =
    schema match {
      case c: Schema.Collection[_, _] if !supportedCollection(c)                                                    =>
        throw new IllegalArgumentException(s"Collection schema $c is not supported for query codecs")
      case enum0: Schema.Enum[_] if !enum0.annotations.exists(_.isInstanceOf[simpleEnum])                           =>
        throw new IllegalArgumentException(s"Enum schema $enum0 is not supported. All cases must be objects.")
      case record: Schema.Record[A] if record.fields.size != 1                                                      =>
        throw new IllegalArgumentException("Use queryAll[A] for records with more than one field")
      case record: Schema.Record[A] if !supportedElementSchema(record.fields.head.schema.asInstanceOf[Schema[Any]]) =>
        throw new IllegalArgumentException(
          s"Only primitive types and simple enums can be used in single field records, but got ${record.fields.head.schema}",
        )
      case other                                                                                                    =>
        HttpCodec.Query(name, other)
    }

  private def supportedCollection(schema: Schema.Collection[_, _]): Boolean = schema match {
    case Schema.Map(_, _, _)                                =>
      false
    case Schema.NonEmptyMap(_, _, _)                        =>
      false
    case Schema.Sequence(elementSchema, _, _, _, _)         =>
      supportedElementSchema(elementSchema.asInstanceOf[Schema[Any]])
    case Schema.NonEmptySequence(elementSchema, _, _, _, _) =>
      supportedElementSchema(elementSchema.asInstanceOf[Schema[Any]])
    case Schema.Set(elementSchema, _)                       =>
      supportedElementSchema(elementSchema.asInstanceOf[Schema[Any]])
  }

  @tailrec
  private def supportedElementSchema(elementSchema: Schema[Any]): Boolean = elementSchema match {
    case Schema.Lazy(schema0) => supportedElementSchema(schema0())
    case _                    =>
      elementSchema.isInstanceOf[Schema.Primitive[_]] ||
      elementSchema.isInstanceOf[Schema.Enum[_]] && elementSchema.annotations.exists(_.isInstanceOf[simpleEnum]) ||
      elementSchema.isInstanceOf[Schema.Record[_]] && elementSchema.asInstanceOf[Schema.Record[_]].fields.size == 1
  }

  def queryAll[A](implicit schema: Schema[A]): QueryCodec[A] =
    schema match {
      case _: Schema.Primitive[A]                                    =>
        throw new IllegalArgumentException("Use query[A](name: String) for primitive types")
      case record: Schema.Record[A]                                  =>
        HttpCodec.Query(record)
      case Schema.Optional(s, _) if s.isInstanceOf[Schema.Record[_]] =>
        HttpCodec.Query(schema)
      case _                                                         =>
        throw new IllegalArgumentException(
          "Only case classes can be used with queryAll. Maybe you wanted to use query[A](name: String)?",
        )
    }

}
