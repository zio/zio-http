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
import java.util.{List => JList}

import zio.http.QueryParams

private[http] object QueryParamEncoding {
  def decode(queryStringFragment: String, charset: Charset): QueryParams = {
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val length = queryStringFragment.length

      val paramCount = {
        // Estimate number of parameters by counting '&' characters
        // This is an estimate, but avoids allocating a list for every parameter
        var count = 1 // At least one parameter exists
        var i     = 0
        while (i < length) {
          if (queryStringFragment.charAt(i) == '&') count += 1
          i += 1
        }
        count
      }

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

  def encode(baseUri: java.lang.StringBuilder, queryParams: QueryParams, charset: Charset): String = {
    if (queryParams.isEmpty) {
      return baseUri.toString
    }

    // Estimate initial capacity to avoid resizing
    val paramCount = queryParams.seq.size
    baseUri.ensureCapacity(baseUri.length + Math.min(paramCount * 20, 1024))

    var isFirst  = true
    val iterator = queryParams.seq.iterator
    while (iterator.hasNext) {
      val entry = iterator.next()
      val key   = entry.getKey

      if (key != "") {
        val values = entry.getValue

        if (values.isEmpty) {
          // Handle key with no value
          if (isFirst) {
            baseUri.append('?')
            isFirst = false
          } else {
            baseUri.append('&')
          }
          encodeComponentInto(key, charset, baseUri)
          baseUri.append('=')
        } else {
          // Handle key with values - use direct iteration for better performance
          var j          = 0
          val valuesSize = values.size()
          while (j < valuesSize) {
            if (isFirst) {
              baseUri.append('?')
              isFirst = false
            } else {
              baseUri.append('&')
            }

            encodeComponentInto(key, charset, baseUri)
            baseUri.append('=')
            encodeComponentInto(values.get(j), charset, baseUri)
            j += 1
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

    ByteArrayOutputStreamPool.withStream { byteBuffer =>
      i = start

      while (i < end) {
        val c = component.charAt(i)
        if (c == '%' && i + 2 < end) {
          // Extract hex digits directly without substring
          val digit1 = Character.digit(component.charAt(i + 1), 16)
          val digit2 = Character.digit(component.charAt(i + 2), 16)

          if (digit1 >= 0 && digit2 >= 0) { // Valid hex digits
            val decoded = (digit1 << 4) | digit2
            byteBuffer.write(decoded)
            i += 3
          } else {
            // Invalid hex encoding, treat % as literal
            val bytes = "%".getBytes(charset)
            byteBuffer.write(bytes, 0, bytes.length)
            i += 1
          }
        } else if (c == '+') {
          val bytes = " ".getBytes(charset)
          byteBuffer.write(bytes, 0, bytes.length)
          i += 1
        } else {
          val bytes = c.toString.getBytes(charset)
          byteBuffer.write(bytes, 0, bytes.length)
          i += 1
        }
      }

      new String(byteBuffer.toByteArray, charset)
    }
  }

  /**
   * Encodes a URL component according to HTML/URL encoding rules. Spaces become
   * '+', and special characters become percent-encoded.
   */
  private def encodeComponentInto(component: String, charset: Charset, target: java.lang.StringBuilder): Unit = {
    if (component.isEmpty) return

    // Fast path for strings that don't need encoding
    var needsEncoding = false
    var i             = 0
    val len           = component.length
    while (i < len && !needsEncoding) {
      val c = component.charAt(i)
      // RFC 3986 unreserved characters plus '*' (Netty-specific addition)
      needsEncoding = !needsNoEncoding(c)
      i += 1
    }

    if (!needsEncoding) {
      target.append(component)
      return
    }

    val bytes    = component.getBytes(charset)
    var k        = 0
    val bytesLen = bytes.length
    while (k < bytesLen) {
      val unsignedByte = bytes(k) & 0xff
      if (
        (unsignedByte >= 'a' && unsignedByte <= 'z') ||
        (unsignedByte >= 'A' && unsignedByte <= 'Z') ||
        (unsignedByte >= '0' && unsignedByte <= '9') ||
        unsignedByte == '-' || unsignedByte == '.' ||
        unsignedByte == '_' || unsignedByte == '~' || unsignedByte == '*'
      ) {
        // Unreserved character
        target.append(unsignedByte.toChar)
      } else if (unsignedByte == ' ') {
        // Space becomes '+'
        target.append('+')
      } else {
        // Percent encoding
        target.append('%')
        target.append(Character.forDigit((unsignedByte >> 4) & 0xf, 16).toUpper)
        target.append(Character.forDigit(unsignedByte & 0xf, 16).toUpper)
      }
      k += 1
    }
  }

  private def needsNoEncoding(c: Char) = {
    (c >= 'a' && c <= 'z') ||
    (c >= 'A' && c <= 'Z') ||
    (c >= '0' && c <= '9') ||
    c == '-' || c == '.' || c == '_' || c == '~' || c == '*'
  }
}
