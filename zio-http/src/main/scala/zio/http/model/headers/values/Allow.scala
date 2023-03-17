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

import scala.annotation.tailrec

import zio.{Chunk, NonEmptyChunk}

import zio.http.model.Method

/**
 * The Allow header must be sent if the server responds with a 405 Method Not
 * Allowed status code to indicate which request methods can be used.
 */
final case class Allow(methods: NonEmptyChunk[Method])

object Allow {
  val OPTIONS: Allow = Allow(NonEmptyChunk.single(Method.OPTIONS))
  val GET: Allow     = Allow(NonEmptyChunk.single(Method.GET))
  val HEAD: Allow    = Allow(NonEmptyChunk.single(Method.HEAD))
  val POST: Allow    = Allow(NonEmptyChunk.single(Method.POST))
  val PUT: Allow     = Allow(NonEmptyChunk.single(Method.PUT))
  val PATCH: Allow   = Allow(NonEmptyChunk.single(Method.PATCH))
  val DELETE: Allow  = Allow(NonEmptyChunk.single(Method.DELETE))
  val TRACE: Allow   = Allow(NonEmptyChunk.single(Method.TRACE))
  val CONNECT: Allow = Allow(NonEmptyChunk.single(Method.CONNECT))

  def parse(value: String): Either[String, Allow] = {
    @tailrec def loop(index: Int, value: String, acc: Chunk[Method]): Either[String, Chunk[Method]] = {
      if (value.isEmpty) Left("Invalid Allow header: empty value")
      else if (index == -1) {
        Method.fromString(value.trim) match {
          case Method.CUSTOM(name) => Left(s"Invalid Allow method: $name")
          case method: Method      => Right(acc :+ method)
        }
      } else {
        val valueChunk     = value.substring(0, index)
        val valueRemaining = value.substring(index + 1)
        val newIndex       = valueRemaining.indexOf(',')

        Method.fromString(valueChunk.trim) match {
          case Method.CUSTOM(name) =>
            Left(s"Invalid Allow method: $name")
          case method: Method      =>
            loop(
              newIndex,
              valueRemaining,
              acc :+ method,
            )
        }
      }
    }

    loop(value.indexOf(','), value, Chunk.empty).flatMap { methods =>
      NonEmptyChunk.fromChunk(methods) match {
        case Some(methods) => Right(Allow(methods))
        case None          => Left("Invalid Allow header: empty value")
      }
    }
  }

  def render(allow: Allow): String =
    allow.methods.map(_.name).mkString(", ")

}
