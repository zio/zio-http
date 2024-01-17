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

package zio.http

import java.security.MessageDigest
import java.util.Base64.getEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.collection.mutable

import zio.{Chunk, Duration}

import zio.http._
import zio.http.internal.CookieEncoding

/**
 * Cookie is an immutable and type-safe representation of an HTTP cookie. There
 * are two types of cookies: request cookies and response cookies. These can be
 * created with the constructors in the companion object of Cookie.
 */
sealed trait Cookie { self =>
  def name: String

  def content: String

  /**
   * Encodes the cookie into a string, or fails with an exception if there was a
   * problem during encoding. This encoding method performs no validation.
   */
  final def encode: Either[Exception, String] = encodeValidate(false)

  /**
   * Encodes the cookie into a string, or fails with an exception if there was a
   * problem during encoding. This encoding method performs validation as
   * specified.
   */
  def encodeValidate(validate: Boolean): Either[Exception, String]

  /**
   * Converts the cookie to a request cookie. If the cookie is a response
   * cookie, then it is converted into a request cookie by discarding everything
   * except the name and content.
   */
  def toRequest: Cookie.Request = Cookie.Request(name, content)

  /**
   * Converts the cookie to a response cookie, using, if necessary, default
   * values for all the parameters of a response cookie.
   */
  def toResponse: Cookie.Response = toResponse()

  /**
   * Converts the cookie to a response cookie, using the specified values only
   * if this cookie is a request cookie.
   */
  def toResponse(
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Duration] = None,
    sameSite: Option[Cookie.SameSite] = None,
  ): Cookie.Response = {
    self match {
      case Cookie.Request(_, _) =>
        Cookie.Response(name, content, domain, path, isSecure, isHttpOnly, maxAge, sameSite)
      case x: Cookie.Response   => x
    }
  }

  /**
   * Returns a new cookie derived from this one, but where the content of the
   * cookie is set to the specified value.
   */
  def content(content: String): Cookie

  /**
   * Returns a new cookie derived from this one, but where the name of the
   * cookie is set to the specified value.
   */
  def name(name: String): Cookie
}

object Cookie {

  /**
   * Creates a cookie with an expired maxAge
   */
  def clear(name: String): Cookie.Response = Cookie.Response(name, "", maxAge = Some(Duration.Zero))

  def decodeRequest(header: String, validate: Boolean = false): Either[Exception, Chunk[Cookie.Request]] =
    Cookie.Request.decode(header, validate)

  def decodeResponse(header: String, validate: Boolean = false): Either[Exception, Cookie.Response] =
    Cookie.Response.decode(header, validate)

  private def signature(secret: String, content: String): String = {
    val sha256    = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(secret.getBytes(), "RSA")

    sha256.init(secretKey)

    val signed = sha256.doFinal(content.getBytes())
    val mda    = MessageDigest.getInstance("SHA-512")
    getEncoder.encodeToString(mda.digest(signed))
  }

  final case class Response(
    name: String,
    content: String,
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Duration] = None,
    sameSite: Option[SameSite] = None,
  ) extends Cookie { self =>
    override def encodeValidate(validate: Boolean): Either[Exception, String] =
      try {
        Right(CookieEncoding.default.encodeResponseCookie(self, validate))
      } catch {
        case e: Exception => Left(e)
      }

    /**
     * Signs cookie content with a secret and returns a signed cookie.
     */
    def sign(secret: String): Cookie.Response =
      content(
        new mutable.StringBuilder()
          .append(content)
          .append('.')
          .append(Cookie.signature(secret, content))
          .result(),
      )

    override def name(name: String): Cookie.Response = copy(name = name)

    override def content(content: String): Cookie.Response = copy(content = content)
  }
  object Response {

    /**
     * Decodes a response cookie from a string.
     */
    def decode(header: String, validate: Boolean = false): Either[Exception, Cookie.Response] = {
      try {
        Right(CookieEncoding.default.decodeResponseCookie(header, validate))
      } catch {
        case e: Exception =>
          Left(e)
      }
    }
  }

  final case class Request(name: String, content: String) extends Cookie { self =>
    override def encodeValidate(validate: Boolean): Either[Exception, String] =
      try {
        Right(CookieEncoding.default.encodeRequestCookie(self, validate))
      } catch {
        case e: Exception => Left(e)
      }

    /**
     * Un-signs cookie content with a secret and returns an unsigned cookie.
     */
    def unSign(secret: String): Option[Cookie.Request] = {
      val index     = content.lastIndexOf('.')
      val signature = content.slice(index + 1, content.length)
      val value     = content.slice(0, index)
      if (Cookie.signature(secret, value) == signature) Some(self.content(value)) else None
    }

    override def name(name: String): Cookie.Request = copy(name = name)

    override def content(content: String): Cookie.Request = copy(content = content)

  }
  object Request {

    /**
     * Decodes a request cookie from a string.
     */
    def decode(header: String, validate: Boolean = false): Either[Exception, Chunk[Cookie.Request]] = {
      try {
        Right(CookieEncoding.default.decodeRequestCookie(header, validate))
      } catch {
        case e: Exception =>
          Left(e)
      }
    }
  }

  sealed trait SameSite
  object SameSite {
    case object Strict extends SameSite
    case object Lax    extends SameSite
    case object None   extends SameSite

    def values: List[SameSite] = List(Strict, Lax, None)
  }
}
