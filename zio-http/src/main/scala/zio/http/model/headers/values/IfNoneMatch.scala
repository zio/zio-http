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

import zio.Chunk

sealed trait IfNoneMatch

object IfNoneMatch {
  case object Any extends IfNoneMatch

  case object None extends IfNoneMatch

  final case class ETags(etags: Chunk[String]) extends IfNoneMatch

  def parse(value: String): Either[String, IfNoneMatch] =
    Right {
      val etags = value.split(",").map(_.trim).toList
      etags match {
        case "*" :: Nil => Any
        case "" :: Nil  => None
        case _          => ETags(Chunk.fromIterable(etags))
      }
    }

  def render(ifMatch: IfNoneMatch): String = ifMatch match {
    case Any          => "*"
    case None         => ""
    case ETags(etags) => etags.mkString(",")
  }
}
