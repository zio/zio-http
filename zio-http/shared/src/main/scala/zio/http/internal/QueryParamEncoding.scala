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

import java.nio.charset.Charset
import java.util
import java.util.{ArrayList, List => JList}

import zio.http.QueryParams

private[http] object QueryParamEncoding {
  def decode(queryStringFragment: String, charset: Charset): QueryParams = {
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val length = queryStringFragment.length

      // Count & characters more idiomatically using Scala's count function
      val paramCount = 1 + queryStringFragment.count(_ == '&')

      // Initialize with exact capacity to avoid rehashing
      val params     = new util.LinkedHashMap[String, JList[String]](paramCount)
      var startIndex = 0

      // Skip a leading '?' if present
      if (startIndex < length && queryStringFragment.charAt(startIndex) == '?') {
        startIndex += 1
      }

      while (startIndex < length) {
        // Find key-value separator ('=') or parameter separator ('&')
        var eqIndex  = -1
        var ampIndex = queryStringFragment.indexOf('&', startIndex)
        if (ampIndex == -1) ampIndex = length

        // Find equals sign within current parameter segment
        var i = startIndex
        while (i < ampIndex && eqIndex == -1) {
          if (queryStringFragment.charAt(i) == '=') eqIndex = i
          i += 1
        }

        // Extract and decode key and value directly using indices
        var key = if (eqIndex != -1) {
          decodeComponent(queryStringFragment, startIndex, eqIndex, charset)
        } else {
          decodeComponent(queryStringFragment, startIndex, ampIndex, charset)
        }

        var value = if (eqIndex != -1) {
          decodeComponent(queryStringFragment, eqIndex + 1, ampIndex, charset)
        } else {
          ""
        }

        // This might seem strange. However, we copied this behavior from Netty.
        if (key == "") {
          key = value
          value = ""
        }

        // Add the parameter to our map
        // Get or create values list for this key without allocating a lambda
        var values = params.get(key)
        if (values == null) {
          // Key doesn't exist yet, create new list
          values = new util.ArrayList[String](1)
          params.put(key, values)
        }
        values.add(value)

        // Move to the next parameter
        startIndex = ampIndex + 1
      }

      QueryParams(params)
    }
  }

  def encode(baseUri: StringBuilder, queryParams: QueryParams, charset: Charset): String = {
    if (queryParams.isEmpty) {
      return baseUri.toString
    }

    // Estimate additional capacity needed for query parameters
    // Initial guess: 1 char for ? + average of 20 chars per param pair
    val additionalCapacity = 1 + (queryParams.seq.size * 20)
    baseUri.ensureCapacity(baseUri.length + additionalCapacity)

    var isFirst = true
    queryParams.seq.foreach { entry =>
      val key    = entry.getKey
      val values = entry.getValue

      if (key != "") {
        if (values.isEmpty) {
          // Handle key with no value
          if (isFirst) {
            baseUri.append('?')
            isFirst = false
          } else {
            baseUri.append('&')
          }
          baseUri.append(encodeComponent(key, charset))
          baseUri.append('=')
        } else {
          // Handle key with one or more values
          import scala.jdk.CollectionConverters._
          values.asScala.foreach { value =>
            if (isFirst) {
              baseUri.append('?')
              isFirst = false
            } else {
              baseUri.append('&')
            }

            baseUri.append(encodeComponent(key, charset))
            baseUri.append('=')
            baseUri.append(encodeComponent(value, charset))
          }
        }
      }
    }
    baseUri.toString
  }

  /**
   * Decodes a URL component according to HTML/URL encoding rules. This handles
   * percent encoding (%xx) and '+' character for spaces. Uses direct indexing
   * to avoid substring allocations.
   */
  private def decodeComponent(component: String, start: Int, end: Int, charset: Charset): String = {
    if (start >= end) return ""

    // Quick check for characters that need decoding
    var needsDecoding = false
    var i             = start
    while (i < end && !needsDecoding) {
      val c = component.charAt(i)
      needsDecoding = c == '%' || c == '+'
      i += 1
    }

    if (!needsDecoding) {
      // No decoding needed, create only one string
      return component.substring(start, end)
    }

    val result = new StringBuilder(end - start) // Pre-allocate with estimated size
    i = start

    while (i < end) {
      val c = component.charAt(i)
      if (c == '%' && i + 2 < end) {
        try {
          // Extract hex digits directly without substring
          val digit1 = Character.digit(component.charAt(i + 1), 16)
          val digit2 = Character.digit(component.charAt(i + 2), 16)

          if (digit1 >= 0 && digit2 >= 0) { // Valid hex digits
            val decoded = (digit1 << 4) | digit2
            result.append(decoded.toChar)
            i += 3
          } else {
            // Invalid hex encoding, treat % as literal
            result.append('%')
            i += 1
          }
        } catch {
          case _: IndexOutOfBoundsException =>
            // Incomplete percent encoding
            result.append('%')
            i += 1
        }
      } else if (c == '+') {
        result.append(' ')
        i += 1
      } else {
        result.append(c)
        i += 1
      }
    }

    // Apply charset conversion only once at the end
    new String(result.toString.getBytes(charset))
  }

  /**
   * Encodes a URL component according to HTML/URL encoding rules. Spaces become
   * '+', and special characters become percent-encoded.
   */
  private def encodeComponent(component: String, charset: Charset): String = {
    if (component.isEmpty) return component

    // Fast path for strings that don't need encoding
    var needsEncoding = false
    var i             = 0
    val len           = component.length
    while (i < len && !needsEncoding) {
      val c = component.charAt(i)
      // RFC 3986 unreserved characters plus '*' (Netty-specific addition)
      needsEncoding = !(
        (c >= 'a' && c <= 'z') ||
          (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') ||
          c == '-' || c == '.' || c == '_' || c == '~' || c == '*'
      )
      i += 1
    }

    if (!needsEncoding) return component

    // Special optimization for UTF-8 (most common case)
    val isUtf8 = charset == java.nio.charset.StandardCharsets.UTF_8 || charset.name().equalsIgnoreCase("UTF-8")

    // Calculate a more accurate capacity based on where encoding becomes necessary
    val estimatedCapacity =
      if (i > 1) {
        // First (i-1) chars don't need encoding + remaining chars might need encoding (worst case: 3x)
        (i - 1) + ((component.length - i + 1) * 3)
      } else {
        // Worst case: all characters need percent encoding
        component.length * 3
      }

    val result = new java.lang.StringBuilder(estimatedCapacity)

    // Copy the characters that we know don't need encoding
    if (i > 1) {
      result.append(component, 0, i - 1)
    }

    // Process remaining characters
    var j = if (i == 0) 0 else i - 1

    if (isUtf8) {
      // Optimized UTF-8 path
      while (j < len) {
        val c = component.charAt(j)
        if (
          (c >= 'a' && c <= 'z') ||
          (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') ||
          c == '-' || c == '.' || c == '_' || c == '~' || c == '*'
        ) {
          // Unreserved character
          result.append(c)
        } else if (c < 128) {
          // ASCII character that needs encoding
          result.append('%')
          result.append(Character.forDigit((c >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(c & 0xf, 16).toUpper)
        } else if (c < 2048) {
          // 2-byte UTF-8 encoding
          val byte1 = 0xc0 | (c >> 6)
          val byte2 = 0x80 | (c & 0x3f)
          result.append('%')
          result.append(Character.forDigit((byte1 >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(byte1 & 0xf, 16).toUpper)
          result.append('%')
          result.append(Character.forDigit((byte2 >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(byte2 & 0xf, 16).toUpper)
        } else {
          // 3-byte UTF-8 encoding
          val byte1 = 0xe0 | (c >> 12)
          val byte2 = 0x80 | ((c >> 6) & 0x3f)
          val byte3 = 0x80 | (c & 0x3f)
          result.append('%')
          result.append(Character.forDigit((byte1 >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(byte1 & 0xf, 16).toUpper)
          result.append('%')
          result.append(Character.forDigit((byte2 >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(byte2 & 0xf, 16).toUpper)
          result.append('%')
          result.append(Character.forDigit((byte3 >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(byte3 & 0xf, 16).toUpper)
        }
        j += 1
      }
    } else {
      // Generic path for other charsets
      val bytes = component.getBytes(charset)

      bytes.foreach { b =>
        val unsignedByte = b & 0xff
        if (
          (unsignedByte >= 'a' && unsignedByte <= 'z') ||
          (unsignedByte >= 'A' && unsignedByte <= 'Z') ||
          (unsignedByte >= '0' && unsignedByte <= '9') ||
          unsignedByte == '-' || unsignedByte == '.' ||
          unsignedByte == '_' || unsignedByte == '~' || unsignedByte == '*'
        ) {
          // Unreserved character
          result.append(unsignedByte.toChar)
        } else if (unsignedByte == ' ') {
          // Space becomes '+'
          result.append('+')
        } else {
          // Percent encoding
          result.append('%')
          result.append(Character.forDigit((unsignedByte >> 4) & 0xf, 16).toUpper)
          result.append(Character.forDigit(unsignedByte & 0xf, 16).toUpper)
        }
      }
    }

    result.toString
  }
}
