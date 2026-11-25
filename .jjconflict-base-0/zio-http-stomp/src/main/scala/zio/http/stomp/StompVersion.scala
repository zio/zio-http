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

package zio.http.stomp

/**
 * STOMP Protocol Version support
 *
 * Supports STOMP versions 1.0, 1.1, and 1.2 as defined by:
 *   - STOMP 1.0: https://stomp.github.io/stomp-specification-1.0.html
 *   - STOMP 1.1: https://stomp.github.io/stomp-specification-1.1.html
 *   - STOMP 1.2: https://stomp.github.io/stomp-specification-1.2.html
 */
sealed trait StompVersion {
  def versionString: String
  def supportsHeaderEscaping: Boolean
  def supportsNack: Boolean
  def requiresContentLength: Boolean
  def supportsHeartbeat: Boolean
}

object StompVersion {

  /**
   * STOMP 1.0 - Original specification
   *   - No header escaping
   *   - No NACK command
   *   - No heartbeat support
   *   - Content-length is optional
   */
  case object V1_0 extends StompVersion {
    val versionString          = "1.0"
    val supportsHeaderEscaping = false
    val supportsNack           = false
    val requiresContentLength  = false
    val supportsHeartbeat      = false
  }

  /**
   * STOMP 1.1 - Added features
   *   - Header escaping added
   *   - NACK command added
   *   - Heartbeat support added
   *   - Content-length is recommended
   */
  case object V1_1 extends StompVersion {
    val versionString          = "1.1"
    val supportsHeaderEscaping = true
    val supportsNack           = true
    val requiresContentLength  = false
    val supportsHeartbeat      = true
  }

  /**
   * STOMP 1.2 - Latest specification (default)
   *   - All features from 1.1
   *   - Improved header escaping rules
   *   - Content-length is mandatory for binary data
   */
  case object V1_2 extends StompVersion {
    val versionString          = "1.2"
    val supportsHeaderEscaping = true
    val supportsNack           = true
    val requiresContentLength  = true
    val supportsHeartbeat      = true
  }

  val all: List[StompVersion] = List(V1_0, V1_1, V1_2)

  def fromString(s: String): Option[StompVersion] = s match {
    case "1.0" => Some(V1_0)
    case "1.1" => Some(V1_1)
    case "1.2" => Some(V1_2)
    case _     => None
  }

  /**
   * Parse accept-version header to get list of supported versions
   */
  def parseAcceptVersions(acceptVersion: String): List[StompVersion] = {
    acceptVersion
      .split(",")
      .map(_.trim)
      .flatMap(fromString)
      .toList
  }

  /**
   * Negotiate version between client accepted versions and server supported
   * versions Returns the highest mutually supported version
   */
  def negotiate(
    clientAcceptVersions: List[StompVersion],
    serverSupportedVersions: List[StompVersion] = all,
  ): Option[StompVersion] = {
    val mutualVersions = clientAcceptVersions.filter(serverSupportedVersions.contains)
    if (mutualVersions.isEmpty) None
    else {
      // Return the highest version (1.2 > 1.1 > 1.0)
      Some(mutualVersions.maxBy {
        case V1_2 => 3
        case V1_1 => 2
        case V1_0 => 1
      })
    }
  }
}
