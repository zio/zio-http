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

package zio.http.model.headers.values

import scala.util.Try

sealed trait Range

object Range {

  final case class Single(unit: String, start: Long, end: Option[Long])       extends Range
  final case class Multiple(unit: String, ranges: List[(Long, Option[Long])]) extends Range
  final case class Suffix(unit: String, value: Long)                          extends Range
  final case class Prefix(unit: String, value: Long)                          extends Range

  def toRange(value: String): Either[String, Range] = {
    val parts = value.split("=")
    if (parts.length != 2) Left("Invalid Range header")
    else {
      Try {
        val unit  = parts(0)
        val range = parts(1)
        if (range.contains(",")) {
          val ranges       = range.split(",").map(_.trim).toList
          val parsedRanges = ranges.map { r =>
            if (r.contains("-")) {
              val startEnd = r.split("-")
              if (startEnd.length != 2) (startEnd(0).toLong, None)
              else {
                val start = startEnd(0).toLong
                val end   = startEnd(1).toLong
                (start, Some(end))
              }
            } else (0L, None)
          }
          Multiple(unit, parsedRanges)
        } else if (range.contains("-")) {
          val startEnd = range.split("-")
          if (startEnd.length != 2)
            Single(unit, startEnd(0).toLong, None)
          else {
            if (startEnd(0).isEmpty)
              Suffix(unit, startEnd(1).toLong)
            else if (startEnd(1).isEmpty)
              Prefix(unit, startEnd(0).toLong)
            else
              Single(unit, startEnd(0).toLong, Some(startEnd(1).toLong))
          }
        } else {
          Suffix(unit, range.toLong)
        }
      }.toEither.left.map(_ => "Invalid Range header")
    }
  }

  def fromRange(range: Range): String = range match {
    case Single(unit, start, end)   => s"$unit=$start-${end.getOrElse("")}"
    case Multiple(unit, ranges)     =>
      s"$unit=${ranges.map { case (start, end) => s"$start-${end.getOrElse("")}" }.mkString(",")}"
    case Suffix(unit, suffixLength) => s"$unit=-$suffixLength"
    case Prefix(unit, prefixLength) => s"$unit=$prefixLength-"
  }

}
