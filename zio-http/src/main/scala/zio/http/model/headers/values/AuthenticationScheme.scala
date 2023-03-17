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

sealed trait AuthenticationScheme {
  val name: String
}

object AuthenticationScheme {

  case object Basic extends AuthenticationScheme {
    override val name: String = "Basic"
  }

  case object Bearer extends AuthenticationScheme {
    override val name: String = "Bearer"
  }

  case object Digest extends AuthenticationScheme {
    override val name: String = "Digest"
  }

  case object HOBA extends AuthenticationScheme {
    override val name: String = "HOBA"
  }

  case object Mutual extends AuthenticationScheme {
    override val name: String = "Mutual"
  }

  case object Negotiate extends AuthenticationScheme {
    override val name: String = "Negotiate"
  }

  case object OAuth extends AuthenticationScheme {
    override val name: String = "OAuth"
  }

  case object Scram     extends AuthenticationScheme {
    override val name: String = "SCRAM"
  }
  case object ScramSha1 extends AuthenticationScheme {
    override val name: String = "SCRAM-SHA-1"
  }

  case object ScramSha256 extends AuthenticationScheme {
    override val name: String = "SCRAM-SHA-256"
  }

  case object Vapid extends AuthenticationScheme {
    override val name: String = "vapid"
  }

  case object `AWS4-HMAC-SHA256` extends AuthenticationScheme {
    override val name: String = "AWS4-HMAC-SHA256"
  }

  def fromAuthenticationScheme(authenticationScheme: AuthenticationScheme): String =
    authenticationScheme.name

  def toAuthenticationScheme(name: String): Either[String, AuthenticationScheme] = {
    name.trim.toUpperCase match {
      case "BASIC"            => Right(Basic)
      case "BEARER"           => Right(Bearer)
      case "DIGEST"           => Right(Digest)
      case "HOBA"             => Right(HOBA)
      case "MUTUAL"           => Right(Mutual)
      case "NEGOTIATE"        => Right(Negotiate)
      case "OAUTH"            => Right(OAuth)
      case "SCRAM"            => Right(Scram)
      case "SCRAM-SHA-1"      => Right(ScramSha1)
      case "SCRAM-SHA-256"    => Right(ScramSha256)
      case "VAPID"            => Right(Vapid)
      case "AWS4-HMAC-SHA256" => Right(`AWS4-HMAC-SHA256`)
      case name: String       => Left(s"Unsupported authentication scheme: $name")
    }
  }

}
