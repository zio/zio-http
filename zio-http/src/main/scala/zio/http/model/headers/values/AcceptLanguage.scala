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
import scala.util.Try
import scala.util.matching.Regex

import zio.Chunk

/**
 * The Accept-Language request HTTP header indicates the natural language and
 * locale that the client prefers.
 */
sealed trait AcceptLanguage

object AcceptLanguage {

  case class AcceptedLanguage(language: String, weight: Option[Double]) extends AcceptLanguage

  case class AcceptedLanguages(languages: Chunk[AcceptLanguage]) extends AcceptLanguage

  case object AnyLanguage extends AcceptLanguage

  case object InvalidAcceptLanguageValue extends AcceptLanguage

  def fromAcceptLanguage(acceptLanguage: AcceptLanguage): String = acceptLanguage match {
    case AcceptedLanguage(language, weight) =>
      val weightString = weight match {
        case Some(w) => s";q=$w"
        case None    => ""
      }
      s"$language$weightString"
    case AcceptedLanguages(languages)       => languages.map(fromAcceptLanguage).mkString(",")
    case AnyLanguage                        => "*"
    case InvalidAcceptLanguageValue         => ""
  }

  def toAcceptLanguage(value: String): AcceptLanguage = {
    @tailrec def loop(index: Int, value: String, acc: AcceptedLanguages): AcceptedLanguages = {
      if (index == -1) acc.copy(languages = acc.languages ++ Chunk(parseAcceptedLanguage(value.trim)))
      else {
        val valueChunk     = value.substring(0, index)
        val valueRemaining = value.substring(index + 1)
        val newIndex       = valueRemaining.indexOf(',')
        loop(
          newIndex,
          valueRemaining,
          acc.copy(languages = acc.languages ++ Chunk(parseAcceptedLanguage(valueChunk.trim))),
        )
      }
    }
    if (validCharacters.findFirstIn(value).isEmpty) InvalidAcceptLanguageValue
    else if (value.isEmpty) InvalidAcceptLanguageValue
    else if (value == "*") AnyLanguage
    else loop(value.indexOf(','), value, AcceptedLanguages(Chunk.empty))
  }

  /**
   * Allowed characters in the header are 0-9, A-Z, a-z, space or *,-.;=
   */
  private val validCharacters: Regex = "^[0-9a-zA-Z *,\\-.;=]+$".r

  private def parseAcceptedLanguage(value: String): AcceptLanguage = {
    val weightIndex = value.indexOf(";q=")
    if (weightIndex != -1) {
      val language = value.substring(0, weightIndex)
      val weight   = value.substring(weightIndex + 3)
      AcceptedLanguage(
        language,
        Try(weight.toDouble).toOption
          .filter(w => w >= 0.0 && w <= 1.0),
      )
    } else AcceptedLanguage(value, None)
  }
}
