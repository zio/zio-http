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

import scala.collection.mutable
import scala.util.Try

sealed trait WWWAuthenticate

object WWWAuthenticate {
  final case class Basic(realm: String, charset: String = "UTF-8")             extends WWWAuthenticate
  final case class Bearer(
    realm: String,
    scope: Option[String] = None,
    error: Option[String] = None,
    errorDescription: Option[String] = None,
  ) extends WWWAuthenticate
  final case class Digest(
    realm: Option[String],
    domain: Option[String] = None,
    nonce: Option[String] = None,
    opaque: Option[String] = None,
    stale: Option[Boolean] = None,
    algorithm: Option[String] = None,
    qop: Option[String] = None,
    charset: Option[String] = None,
    userhash: Option[Boolean] = None,
  ) extends WWWAuthenticate
  final case class HOBA(realm: Option[String], challenge: String, maxAge: Int) extends WWWAuthenticate
  final case class Mutual(realm: String, error: Option[String] = None, errorDescription: Option[String] = None)
      extends WWWAuthenticate
  final case class Negotiate(authData: Option[String] = None)                  extends WWWAuthenticate

  final case class SCRAM(
    realm: String,
    sid: String,
    data: String,
  ) extends WWWAuthenticate
  final case class `AWS4-HMAC-SHA256`(
    realm: String,
    credentials: Option[String] = None,
    signedHeaders: String,
    signature: String,
  ) extends WWWAuthenticate
  final case class Unknown(scheme: String, realm: String, params: Map[String, String]) extends WWWAuthenticate

  private val challengeRegEx  = """(\w+) (.*)""".r
  private val auth            = """(\w+)=(?:"([^"]+)"|([^,]+))""".r
  private val nonQuotedValues = Set("max_age", "stale", "userhash", "algorithm", "charset")

  def parse(value: String): Either[String, WWWAuthenticate] =
    Try {
      val challengeRegEx(scheme, challenge) = value
      val params                            = auth
        .findAllMatchIn(challenge)
        .map { m =>
          val key   = m.group(1)
          val value = Option(m.group(2)).getOrElse(m.group(3))
          key -> value
        }
        .toMap

      AuthenticationScheme.parse(scheme).map {
        case AuthenticationScheme.Basic              =>
          Basic(params("realm"), params.getOrElse("charset", "UTF-8"))
        case AuthenticationScheme.Bearer             =>
          Bearer(
            realm = params("realm"),
            scope = params.get("scope"),
            error = params.get("error"),
            errorDescription = params.get("error_description"),
          )
        case AuthenticationScheme.Digest             =>
          Digest(
            realm = params.get("realm"),
            domain = params.get("domain"),
            nonce = params.get("nonce"),
            opaque = params.get("opaque"),
            stale = params.get("stale").map(_.toBoolean),
            algorithm = params.get("algorithm"),
            qop = params.get("qop"),
            charset = params.get("charset"),
            userhash = params.get("userhash").map(_.toBoolean),
          )
        case AuthenticationScheme.HOBA               =>
          HOBA(
            realm = params.get("realm"),
            challenge = params("challenge"),
            maxAge = params("max_age").toInt,
          )
        case AuthenticationScheme.Mutual             =>
          Mutual(
            realm = params("realm"),
            error = params.get("error"),
            errorDescription = params.get("error_description"),
          )
        case AuthenticationScheme.Negotiate          =>
          Negotiate(Some(challenge))
        case AuthenticationScheme.Scram              =>
          SCRAM(
            realm = params("realm"),
            sid = params("sid"),
            data = params("data"),
          )
        case AuthenticationScheme.`AWS4-HMAC-SHA256` =>
          `AWS4-HMAC-SHA256`(
            realm = params("realm"),
            credentials = params.get("credentials"),
            signedHeaders = params("signedHeaders"),
            signature = params("signature"),
          )
        case _                                       =>
          Unknown(scheme, params("realm"), params)
      }
    }.toEither.left.map(_ => s"Invalid WWW-Authenticate header").flatMap {
      case Right(value) => Right(value)
      case Left(value)  => Left(value)
    }

  def render(wwwAuthenticate: WWWAuthenticate): String        = {
    val (scheme, params) = wwwAuthenticate match {
      case Basic(realm, charset)                                                          =>
        "Basic" -> mutable.LinkedHashMap("realm" -> realm, charset -> charset)
      case Bearer(realm, scope, error, errorDescription)                                  =>
        "Bearer" -> mutable.LinkedHashMap(
          "realm"             -> realm,
          "scope"             -> scope.getOrElse(""),
          "error"             -> error.getOrElse(""),
          "error_description" -> errorDescription.getOrElse(""),
        )
      case Digest(realm, domain, nonce, opaque, stale, algorithm, qop, charset, userhash) =>
        "Digest" -> mutable.LinkedHashMap(
          "realm"     -> realm.getOrElse(""),
          "domain"    -> domain.getOrElse(""),
          "nonce"     -> nonce.getOrElse(""),
          "opaque"    -> opaque.getOrElse(""),
          "stale"     -> stale.getOrElse(false).toString,
          "algorithm" -> algorithm.getOrElse(""),
          "qop"       -> qop.getOrElse(""),
          "charset"   -> charset.getOrElse(""),
          "userhash"  -> userhash.getOrElse(false).toString,
        )
      case HOBA(realm, challenge, maxAge)                                                 =>
        "HOBA" -> mutable.LinkedHashMap(
          "realm"     -> realm.getOrElse(""),
          "challenge" -> challenge,
          "max_age"   -> maxAge.toString,
        )
      case Mutual(realm, error, errorDescription)                                         =>
        "Mutual" -> mutable.LinkedHashMap(
          "realm"             -> realm,
          "error"             -> error.getOrElse(""),
          "error_description" -> errorDescription.getOrElse(""),
        )
      case Negotiate(authData)                                                            =>
        "Negotiate" -> mutable.LinkedHashMap(
          "" -> authData.getOrElse(""),
        )
      case SCRAM(realm, sid, data)                                                        =>
        "SCRAM" -> mutable.LinkedHashMap(
          "realm" -> realm,
          "sid"   -> sid,
          "data"  -> data,
        )
      case `AWS4-HMAC-SHA256`(realm, credentials, signedHeaders, signature)               =>
        "AWS4-HMAC-SHA256" -> mutable.LinkedHashMap(
          "realm"         -> realm,
          "credentials"   -> credentials.getOrElse(""),
          "signedHeaders" -> signedHeaders,
          "signature"     -> signature,
        )
      case Unknown(scheme, _, params)                                                     =>
        scheme -> params
    }
    scheme + params.filter { case (_, v) => v.nonEmpty }.map { case (k, v) =>
      if (k.isEmpty) s"$v" else s"$k=${formatValue(k, v)}"
    }
      .mkString(" ", ", ", "")
  }
  private def formatValue(key: String, value: String): String = {
    if (nonQuotedValues.contains(key)) value else "\"" + value + "\""
  }
}
