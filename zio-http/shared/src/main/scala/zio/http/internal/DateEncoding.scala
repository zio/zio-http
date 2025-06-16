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

package zio.http.internal

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

private[http] object DateEncoding {
  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
  private val `'0'`     = 48 // ASCII code for '0'

  def encodeDate(date: ZonedDateTime): String = formatter.format(date)

  def decodeDate(date: String): Option[ZonedDateTime] = {
    try {
      if (date.head.isUpper && date.charAt(3) == ',' && date.charAt(4) == ' ')
        decodeRFC1123(date)
      else
        Some(ZonedDateTime.parse(date, formatter))
    } catch {
      case _: Exception => None
    }
  }

  private def decodeRFC1123(date: String): Option[ZonedDateTime] = {
    {
      // TODO consider trie-hard approach
      val c0 = date.head
      val c1 = date.charAt(1)
      val c2 = date.charAt(2)
      if (
        (c0 == 'S' && c1 == 'u' && c2 == 'n') ||
        (c0 == 'M' && c1 == 'o' && c2 == 'n') ||
        (c0 == 'T' && c1 == 'u' && c2 == 'e') ||
        (c0 == 'W' && c1 == 'e' && c2 == 'd') ||
        (c0 == 'T' && c1 == 'h' && c2 == 'u') ||
        (c0 == 'F' && c1 == 'r' && c2 == 'i') ||
        (c0 == 'S' && c1 == 'a' && c2 == 't')
      ) {}
      else return None
    }

    var offset = 0

    val dayOfMonth: Int = {
      val d1         = date.charAt(5)
      val d2         = date.charAt(6)
      if (d2 == ' ') offset = -1
      var maybe: Int = 0
      d1 match {
        case '0'                => ()
        case '1' if offset == 0 => maybe += 10
        case '1'                => maybe += 1
        case '2' if offset == 0 => maybe += 20
        case '2'                => maybe += 2
        case '3' if offset == 0 => maybe += 30
        case '3'                => maybe += 3
        // If the offset is 0 we expect a two-digit day
        // so we return None if the first digit is not 1, 2, or 3, because there are no months with
        // more than 31 days.
        case _ if offset == 0   => return None
        case '4'                => maybe += 4
        case '5'                => maybe += 5
        case '6'                => maybe += 6
        case '7'                => maybe += 7
        case '8'                => maybe += 8
        case '9'                => maybe += 9
        case _                  => return None
      }
      if (offset == 0) {
        d2 match {
          case '0' => maybe += 0
          case '1' => maybe += 1
          case '2' => maybe += 2
          case '3' => maybe += 3
          case '4' => maybe += 4
          case '5' => maybe += 5
          case '6' => maybe += 6
          case '7' => maybe += 7
          case '8' => maybe += 8
          case '9' => maybe += 9
          case _   => return None
        }
      }
      if (maybe < 1 || maybe > 31) return None
      maybe
    }

    val c7 = date.charAt(7 + offset)

    if (c7 != ' ') return None

    val month: Int = {
      val m1  = date.charAt(8 + offset)
      val m2  = date.charAt(9 + offset)
      val m3  = date.charAt(10 + offset)
      val mon =
        if (m1 == 'J' && m2 == 'a' && m3 == 'n') 1
        else if (m1 == 'F' && m2 == 'e' && m3 == 'b') 2
        else if (m1 == 'M' && m2 == 'a' && m3 == 'r') 3
        else if (m1 == 'A' && m2 == 'p' && m3 == 'r') 4
        else if (m1 == 'M' && m2 == 'a' && m3 == 'y') 5
        else if (m1 == 'J' && m2 == 'u' && m3 == 'n') 6
        else if (m1 == 'J' && m2 == 'u' && m3 == 'l') 7
        else if (m1 == 'A' && m2 == 'u' && m3 == 'g') 8
        else if (m1 == 'S' && m2 == 'e' && m3 == 'p') 9
        else if (m1 == 'O' && m2 == 'c' && m3 == 't') 10
        else if (m1 == 'N' && m2 == 'o' && m3 == 'v') 11
        else if (m1 == 'D' && m2 == 'e' && m3 == 'c') 12
        else return None
      mon
    }

    if (date.charAt(11 + offset) != ' ') return None

    val year: Int = {
      val y1 = date.charAt(12 + offset)
      val y2 = date.charAt(13 + offset)
      val y3 = date.charAt(14 + offset)
      val y4 = date.charAt(15 + offset)

      if (y1.isDigit && y2.isDigit && y3.isDigit && y4.isDigit) {
        (y1 - `'0'`) * 1000 + (y2 - `'0'`) * 100 + (y3 - `'0'`) * 10 + (y4 - `'0'`)
      } else {
        return None
      }
    }

    if (date.charAt(16 + offset) != ' ') return None

    val hour: Int = {
      val h1 = date.charAt(17 + offset)
      val h2 = date.charAt(18 + offset)
      if (h1.isDigit && h2.isDigit) {
        (h1 - `'0'`) * 10 + (h2 - `'0'`)
      } else {
        return None
      }
    }

    if (date.charAt(19 + offset) != ':') return None

    val minute: Int = {
      val m1 = date.charAt(20 + offset)
      val m2 = date.charAt(21 + offset)
      if (m1.isDigit && m2.isDigit) {
        (m1 - `'0'`) * 10 + (m2 - `'0'`)
      } else {
        return None
      }
    }

    if (date.charAt(22 + offset) != ':') return None

    val second: Int = {
      val s1 = date.charAt(23 + offset)
      val s2 = date.charAt(24 + offset)
      if (s1.isDigit && s2.isDigit) {
        (s1 - `'0'`) * 10 + (s2 - `'0'`)
      } else {
        return None
      }
    }

    Some(ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, 0, ZoneOffset.UTC))
  }

}
