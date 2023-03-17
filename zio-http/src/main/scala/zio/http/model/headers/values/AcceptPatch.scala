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

import zio.{Chunk, NonEmptyChunk}

import zio.http.model.MediaType

/**
 * The Accept-Patch response HTTP header advertises which media-type the server
 * is able to understand in a PATCH request.
 */
final case class AcceptPatch(mediaTypes: NonEmptyChunk[MediaType])

object AcceptPatch {

  def parse(value: String): Either[String, AcceptPatch] =
    if (value.nonEmpty) {
      val parsedMediaTypes = Chunk
        .fromArray(
          value
            .split(",")
            .map(mediaTypeStr =>
              MediaType
                .forContentType(mediaTypeStr)
                .getOrElse(
                  MediaType
                    .parseCustomMediaType(mediaTypeStr)
                    .orNull,
                ),
            ),
        )
        .filter(_ != null)

      NonEmptyChunk.fromChunk(parsedMediaTypes) match {
        case Some(value) => Right(AcceptPatch(value))
        case None        => Left("Invalid Accept-Patch header")
      }
    } else Left("Accept-Patch header cannot be empty")

  def render(acceptPatch: AcceptPatch): String =
    acceptPatch.mediaTypes.map(_.fullType).mkString(",")

}
