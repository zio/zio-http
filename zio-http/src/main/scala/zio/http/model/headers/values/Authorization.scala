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

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.annotation.tailrec
import scala.util.Try

import zio.http.model.headers.values.Authorization.AuthScheme.{Basic, Bearer, Digest, Unparsed}

/** Authorization header value. */
sealed trait Authorization

object Authorization {

  sealed trait AuthScheme

  object AuthScheme {
    final case class Basic(username: String, password: String)            extends AuthScheme
    final case class Digest(
      response: String,
      username: String,
      realm: String,
      uri: URI,
      opaque: String,
      algorithm: String,
      qop: String,
      cnonce: String,
      nonce: String,
      nc: Int,
      userhash: Boolean,
    ) extends AuthScheme
    final case class Bearer(token: String)                                extends AuthScheme
    final case class Unparsed(authScheme: String, authParameters: String) extends AuthScheme
  }

  /**
   * The Authorization header value contains one of the auth schemes
   * [[AuthScheme]].
   */
  final case class AuthorizationValue(authScheme: AuthScheme) extends Authorization

  /** The Authorization header value is invalid. */
  case object InvalidAuthorizationValue extends Authorization

  def fromAuthorization(header: Authorization): String = header match {
    case AuthorizationValue(Basic(username, password)) =>
      s"Basic ${Base64.getEncoder.encodeToString(s"$username:$password".getBytes(StandardCharsets.UTF_8))}"

    case AuthorizationValue(Digest(response, username, realm, uri, opaque, algo, qop, cnonce, nonce, nc, userhash)) =>
      s"""$Digest response="$response",username="$username",realm="$realm",uri=${uri.toString},opaque="$opaque",algorithm=$algo,""" +
        s"""qop=$qop,cnonce="$cnonce",nonce="$nonce",nc=$nc,userhash=${userhash.toString}"""
    case AuthorizationValue(Bearer(token))            => s"$Bearer $token"
    case AuthorizationValue(Unparsed(scheme, params)) => s"$scheme $params"
    case InvalidAuthorizationValue                    => ""
  }

  def toAuthorization(value: String): Authorization = {
    val parts = value.split(' ')
    if (parts.length >= 2) {
      parts(0).toLowerCase match {
        case "basic"  => parseBasic(parts(1))
        case "digest" => parseDigest(parts.tail.mkString(" "))
        case "bearer" => AuthorizationValue(Bearer(parts(1)))
        case _        => AuthorizationValue(Unparsed(parts(0), parts.tail.mkString(" ")))
      }
    } else InvalidAuthorizationValue
  }

  private def parseBasic(value: String) = {
    val partsOfBasic = new String(Base64.getDecoder.decode(value)).split(':')
    if (partsOfBasic.length == 2) {
      AuthorizationValue(Basic(partsOfBasic(0), partsOfBasic(1)))
    } else {
      InvalidAuthorizationValue
    }
  }

  private final val quotationMarkChar = "\""
  private final val commaChar         = ","
  private final val equalsChar        = '='

  // https://datatracker.ietf.org/doc/html/rfc7616
  private def parseDigest(value: String) = try {
    def parseDigestKey(index: Int): (String, Int) = {
      val equalsIndex = value.indexOf(equalsChar, index)
      val currentKey  = value.substring(index, equalsIndex).toLowerCase.trim
      (currentKey, equalsIndex + 1)
    }

    def parseDigestValue(index: Int): (String, Int) = {
      val endChar           = if (value(index) == '"') quotationMarkChar else commaChar
      val maybeEndCharIndex = value.indexOf(endChar, index + 1)
      val endCharIndex      = if (maybeEndCharIndex == -1) value.length else maybeEndCharIndex
      val currentValue      = value.substring(index, endCharIndex).stripPrefix(quotationMarkChar)
      val newIndex          = if (endChar == commaChar) endCharIndex + 1 else endCharIndex + 2
      (currentValue, newIndex)
    }

    @tailrec
    def go(index: Int = 0, paramsAcc: Map[String, String] = Map.empty): Map[String, String] = if (
      index < value.length
    ) {
      val (key, tmpIndex)   = parseDigestKey(index)
      val (value, newIndex) = parseDigestValue(tmpIndex)
      go(newIndex, paramsAcc + (key -> value))
    } else paramsAcc

    val params = go()

    val maybeDigest = for {
      response <- params.get("response")
      userhash <- params.get("userhash").flatMap(v => Try(v.toBoolean).toOption).orElse(Some(false))
      username     = params.get("username")
      usernameStar = params.get("username*")
      usernameFinal <-
        if (username.isDefined && usernameStar.isEmpty) {
          username
        } else if (username.isEmpty && usernameStar.isDefined && !userhash) {
          usernameStar
        } else {
          None
        }
      realm         <- params.get("realm")
      uri           <- params.get("uri").flatMap(v => Try(new URI(v)).toOption)
      opaque        <- params.get("opaque")
      algo          <- params.get("algorithm")
      qop           <- params.get("qop")
      cnonce        <- params.get("cnonce")
      nonce         <- params.get("nonce")
      nc            <- params.get("nc").flatMap(v => Try(v.toInt).toOption)
    } yield Digest(response, usernameFinal, realm, uri, opaque, algo, qop, cnonce, nonce, nc, userhash)

    maybeDigest.map(AuthorizationValue(_)).getOrElse(InvalidAuthorizationValue)
  } catch {
    case _: IndexOutOfBoundsException => InvalidAuthorizationValue
  }
}
