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

sealed trait ContentLanguage

object ContentLanguage {
  case object Arabic     extends ContentLanguage
  case object Bulgarian  extends ContentLanguage
  case object Catalan    extends ContentLanguage
  case object Chinese    extends ContentLanguage
  case object Croatian   extends ContentLanguage
  case object Czech      extends ContentLanguage
  case object Danish     extends ContentLanguage
  case object Dutch      extends ContentLanguage
  case object English    extends ContentLanguage
  case object Estonian   extends ContentLanguage
  case object Finnish    extends ContentLanguage
  case object French     extends ContentLanguage
  case object German     extends ContentLanguage
  case object Greek      extends ContentLanguage
  case object Hebrew     extends ContentLanguage
  case object Hindi      extends ContentLanguage
  case object Hungarian  extends ContentLanguage
  case object Icelandic  extends ContentLanguage
  case object Indonesian extends ContentLanguage
  case object Italian    extends ContentLanguage
  case object Japanese   extends ContentLanguage
  case object Korean     extends ContentLanguage
  case object Latvian    extends ContentLanguage
  case object Lithuanian extends ContentLanguage
  case object Norwegian  extends ContentLanguage
  case object Polish     extends ContentLanguage
  case object Portuguese extends ContentLanguage
  case object Romanian   extends ContentLanguage
  case object Russian    extends ContentLanguage
  case object Serbian    extends ContentLanguage
  case object Slovak     extends ContentLanguage
  case object Slovenian  extends ContentLanguage
  case object Spanish    extends ContentLanguage
  case object Swedish    extends ContentLanguage
  case object Thai       extends ContentLanguage
  case object Turkish    extends ContentLanguage
  case object Ukrainian  extends ContentLanguage
  case object Vietnamese extends ContentLanguage

  def toContentLanguage(value: CharSequence): Either[String, ContentLanguage] =
    value.toString.toLowerCase.take(2) match {
      case "ar" => Right(Arabic)
      case "bg" => Right(Bulgarian)
      case "ca" => Right(Catalan)
      case "zh" => Right(Chinese)
      case "hr" => Right(Croatian)
      case "cs" => Right(Czech)
      case "da" => Right(Danish)
      case "nl" => Right(Dutch)
      case "en" => Right(English)
      case "et" => Right(Estonian)
      case "fi" => Right(Finnish)
      case "fr" => Right(French)
      case "de" => Right(German)
      case "el" => Right(Greek)
      case "he" => Right(Hebrew)
      case "hi" => Right(Hindi)
      case "hu" => Right(Hungarian)
      case "is" => Right(Icelandic)
      case "id" => Right(Indonesian)
      case "it" => Right(Italian)
      case "ja" => Right(Japanese)
      case "ko" => Right(Korean)
      case "lv" => Right(Latvian)
      case "lt" => Right(Lithuanian)
      case "nb" => Right(Norwegian)
      case "pl" => Right(Polish)
      case "pt" => Right(Portuguese)
      case "ro" => Right(Romanian)
      case "ru" => Right(Russian)
      case "sr" => Right(Serbian)
      case "sk" => Right(Slovak)
      case "sl" => Right(Slovenian)
      case "es" => Right(Spanish)
      case "sv" => Right(Swedish)
      case "th" => Right(Thai)
      case "tr" => Right(Turkish)
      case "uk" => Right(Ukrainian)
      case "vi" => Right(Vietnamese)
      case _    => Left(s"Invalid ContentLanguage: $value")
    }

  def fromContentLanguage(contentLanguage: ContentLanguage): String =
    contentLanguage match {
      case Arabic     => "ar"
      case Bulgarian  => "bg"
      case Catalan    => "ca"
      case Chinese    => "zh"
      case Croatian   => "hr"
      case Czech      => "cs"
      case Danish     => "da"
      case Dutch      => "nl"
      case English    => "en"
      case Estonian   => "et"
      case Finnish    => "fi"
      case French     => "fr"
      case German     => "de"
      case Greek      => "el"
      case Hebrew     => "he"
      case Hindi      => "hi"
      case Hungarian  => "hu"
      case Icelandic  => "is"
      case Indonesian => "id"
      case Italian    => "it"
      case Japanese   => "ja"
      case Korean     => "ko"
      case Latvian    => "lv"
      case Lithuanian => "lt"
      case Norwegian  => "no"
      case Polish     => "pl"
      case Portuguese => "pt"
      case Romanian   => "ro"
      case Russian    => "ru"
      case Serbian    => "sr"
      case Slovak     => "sk"
      case Slovenian  => "sl"
      case Spanish    => "es"
      case Swedish    => "sv"
      case Thai       => "th"
      case Turkish    => "tr"
      case Ukrainian  => "uk"
      case Vietnamese => "vi"
    }
}
