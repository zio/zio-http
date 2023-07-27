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

import zio.NonEmptyChunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[codec] trait QueryCodecs { self =>
  def queryMany(name: String): QueryCodec[NonEmptyChunk[String]] =
    HttpCodec.Query(name)

  def query(name: String): QueryCodec[String] =
    queryMany(name).transform[String](_.head, NonEmptyChunk.single)

  def queryBool(name: String): QueryCodec[Boolean] =
    self.query(name).transformOrFailLeft(
      raw =>
        try Right(raw.toBoolean)
        catch { case _: IllegalArgumentException => Left("Failed to read query parameter as Boolean") },
      _.toString,
    )

  def queryInt(name: String): QueryCodec[Int] =
    self.query(name).transformOrFailLeft(
      raw =>
        try Right(raw.toInt)
        catch { case _: NumberFormatException => Left("Failed to read query parameter as Int") },
      _.toString,
    )

  def queryLong(name: String): QueryCodec[Long] =
    self.query(name).transformOrFailLeft(
      raw =>
        try Right(raw.toLong)
        catch { case _: NumberFormatException => Left("Failed to read query parameter as Long") },
      _.toString,
    )

  def queryBigDecimal(name: String): QueryCodec[BigDecimal] =
    self.query(name).transformOrFailLeft(
      raw =>
        try Right(BigDecimal(raw))
        catch { case _: NumberFormatException => Left("Failed to read query parameter as BigDecimal") },
      _.toString,
    )

  def queryDouble(name: String): QueryCodec[Double] =
    self.query(name).transformOrFailLeft(
      raw =>
        try Right(raw.toDouble)
        catch { case _: NumberFormatException => Left("Failed to read query parameter as Double") },
      _.toString,
    )

  def queryFloat(name: String): QueryCodec[Float] =
    self.query(name).transformOrFailLeft(
      raw =>
        try Right(raw.toFloat)
        catch { case _: NumberFormatException => Left("Failed to read query parameter as Float") },
      _.toString,
    )
}
