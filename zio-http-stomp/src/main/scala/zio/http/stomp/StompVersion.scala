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

  /**
   * The version as text: `1.0`, `1.1` or `1.2`. `StompVersion.fromString`
   * parses it back.
   */
  def versionString: String

  /**
   * Whether this version of the protocol has header escaping.
   * `StompFrame.encode` and `StompFrame.decode` do not read this flag.
   */
  def supportsHeaderEscaping: Boolean

  /**
   * Whether this version of the protocol has the `NACK` command.
   * `StompFrame.nack` does not read this flag.
   */
  def supportsNack: Boolean

  /**
   * Whether this version of the protocol requires a `content-length` header.
   * `StompFrame.encode` and `StompFrame.decode` do not read this flag.
   */
  def requiresContentLength: Boolean

  /**
   * Whether this version of the protocol has heart-beating.
   * `StompFrame.heartbeat` does not read this flag.
   */
  def supportsHeartbeat: Boolean
}

/**
 * The `StompVersion` cases (STOMP 1.0, 1.1 and 1.2), the list of all of them,
 * parsing from text and version negotiation.
 */
object StompVersion {

  /**
   * STOMP 1.0. Its flags say: no header escaping, no `NACK` command, no
   * heart-beating, and `content-length` not required.
   */
  case object V1_0 extends StompVersion {

    /**
     * The version text, `"1.0"`.
     */
    val versionString = "1.0"

    /**
     * `false`.
     */
    val supportsHeaderEscaping = false

    /**
     * `false`.
     */
    val supportsNack = false

    /**
     * `false`.
     */
    val requiresContentLength = false

    /**
     * `false`.
     */
    val supportsHeartbeat = false
  }

  /**
   * STOMP 1.1. Its flags say: header escaping, the `NACK` command and
   * heart-beating are supported, and `content-length` is not required.
   */
  case object V1_1 extends StompVersion {

    /**
     * The version text, `"1.1"`.
     */
    val versionString = "1.1"

    /**
     * `true`.
     */
    val supportsHeaderEscaping = true

    /**
     * `true`.
     */
    val supportsNack = true

    /**
     * `false`.
     */
    val requiresContentLength = false

    /**
     * `true`.
     */
    val supportsHeartbeat = true
  }

  /**
   * STOMP 1.2, the newest of the three versions. Its flags say: header
   * escaping, the `NACK` command and heart-beating are supported, and
   * `content-length` is required. `StompFrame.connect` and
   * `StompFrame.connected` write `1.2` by default.
   */
  case object V1_2 extends StompVersion {

    /**
     * The version text, `"1.2"`.
     */
    val versionString = "1.2"

    /**
     * `true`.
     */
    val supportsHeaderEscaping = true

    /**
     * `true`.
     */
    val supportsNack = true

    /**
     * `true`.
     */
    val requiresContentLength = true

    /**
     * `true`.
     */
    val supportsHeartbeat = true
  }

  /**
   * The supported versions, `V1_0`, `V1_1` and `V1_2`, in that order.
   */
  val all: List[StompVersion] = List(V1_0, V1_1, V1_2)

  /**
   * Returns the version whose text is exactly `1.0`, `1.1` or `1.2`, and `None`
   * for any other text, including text with surrounding spaces.
   */
  def fromString(s: String): Option[StompVersion] = s match {
    case "1.0" => Some(V1_0)
    case "1.1" => Some(V1_1)
    case "1.2" => Some(V1_2)
    case _     => None
  }

  /**
   * Parses the value of an `accept-version` header. It splits `acceptVersion`
   * at commas, trims each part and keeps the parts that `fromString` accepts,
   * in their original order; unknown parts are dropped.
   */
  def parseAcceptVersions(acceptVersion: String): List[StompVersion] = {
    acceptVersion
      .split(",")
      .map(_.trim)
      .flatMap(fromString)
      .toList
  }

  /**
   * Returns the highest version that is in both `clientAcceptVersions` and
   * `serverSupportedVersions` (`1.2` above `1.1` above `1.0`), or `None` when
   * no version is in both. `serverSupportedVersions` defaults to `all`.
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
