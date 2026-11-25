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

import zio._

import zio.http.internal.CookieEncoding

/**
 * Cookie is an immutable and type-safe representation of an HTTP cookie. There
 * are two types of cookies: request cookies and response cookies. These can be
 * created with the constructors in the companion object of Cookie.
 *
 * {{{
 * val cookie  = Cookie.Response("session", "abc", path = Some(Path("/")), isHttpOnly = true)
 * val header  = cookie.encode // Right(session=abc; Path=/; HTTPOnly)
 * val request = Cookie.Request("theme", "dark")
 * val decoded = Cookie.decodeRequest("theme=dark; lang=en") // Right(Chunk(Request(theme,dark), Request(lang,en)))
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/headers/session/cookies/ Cookies]]
 */
sealed trait Cookie { self =>

  /** The name of the cookie. */
  def name: String

  /** The value of the cookie. */
  def content: String

  /**
   * Encodes the cookie into a string, or fails with an exception if there was a
   * problem during encoding. This encoding method performs no validation. A
   * request cookie is encoded as `name=content`. A response cookie adds
   * `Domain`, `Path`, `Max-Age` (in whole seconds), `Secure`, `HTTPOnly` and
   * `SameSite` attributes, separated by `; `, for the parts that are set. A
   * `null` content gives a `Left`.
   */
  final def encode: Either[Exception, String] = encodeValidate(false)

  /**
   * Encodes the cookie into a string, or fails with an exception if there was a
   * problem during encoding. This encoding method performs validation as
   * specified. With `validate` set to `true`, a name or a content that has a
   * character outside the allowed set gives a `Left` with an
   * `IllegalArgumentException`. With `false` it is the same as `encode`.
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

/**
 * Constructors and decoders for cookies.
 */
object Cookie {

  /**
   * Creates a cookie with an expired maxAge
   */
  def clear(name: String): Cookie.Response = Cookie.Response(name, "", maxAge = Some(Duration.Zero))

  /**
   * Decodes the value of a `Cookie` request header into request cookies. The
   * header is split at `;`, each part is trimmed, and a part is split at the
   * first `=` into a name and a content. Parts without an `=` are skipped, and
   * with `validate` set to `true` so are the parts whose name or content has a
   * character outside the allowed set.
   */
  def decodeRequest(header: String, validate: Boolean = false): Either[Exception, Chunk[Cookie.Request]] =
    Cookie.Request.decode(header, validate)

  /**
   * Decodes the value of a `Set-Cookie` response header into a response cookie.
   * The first part must have the form `name=content`, otherwise the result is a
   * `Left` with an `IllegalArgumentException` whose message is
   * `Invalid Set-Cookie header`. The attributes `Domain`, `Path`, `Max-Age`,
   * `Secure`, `HttpOnly` and `SameSite` are read ignoring case, and other
   * attributes, such as `Expires`, and a `Max-Age` that is not a number are
   * ignored. With `validate` set to `true`, a name or content with a character
   * outside the allowed set, or an unknown `SameSite` value, gives a `Left`.
   */
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

  /**
   * A cookie sent by a server in a `Set-Cookie` header.
   *
   * @param name
   *   the name of the cookie
   * @param content
   *   the value of the cookie
   * @param domain
   *   the `Domain` attribute
   * @param path
   *   the `Path` attribute
   * @param isSecure
   *   whether the `Secure` attribute is set
   * @param isHttpOnly
   *   whether the `HTTPOnly` attribute is set
   * @param maxAge
   *   the `Max-Age` attribute
   * @param sameSite
   *   the `SameSite` attribute
   */
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
        Right(CookieEncoding.encodeResponseCookie(self, validate))
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

  /** Decoder for response cookies. */
  object Response {

    /**
     * Decodes a response cookie from a string.
     */
    def decode(header: String, validate: Boolean = false): Either[Exception, Cookie.Response] = {
      try {
        Right(CookieEncoding.decodeResponseCookie(header, validate))
      } catch {
        case e: Exception =>
          Left(e)
      }
    }
  }

  /**
   * A cookie sent by a client in a `Cookie` header.
   *
   * @param name
   *   the name of the cookie
   * @param content
   *   the value of the cookie
   */
  final case class Request(name: String, content: String) extends Cookie { self =>
    override def encodeValidate(validate: Boolean): Either[Exception, String] =
      try {
        Right(CookieEncoding.encodeRequestCookie(self, validate))
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

  /** Decoder for request cookies. */
  object Request {

    /**
     * Decodes a request cookie from a string.
     */
    def decode(header: String, validate: Boolean = false): Either[Exception, Chunk[Cookie.Request]] = {
      try {
        Right(CookieEncoding.decodeRequestCookie(header, validate))
      } catch {
        case e: Exception =>
          Left(e)
      }
    }
  }

  /** The value of the `SameSite` attribute of a response cookie. */
  sealed trait SameSite

  /** The `SameSite` values. */
  object SameSite {

    /** The `SameSite=Strict` attribute. */
    case object Strict extends SameSite

    /** The `SameSite=Lax` attribute. */
    case object Lax extends SameSite

    /** The `SameSite=None` attribute. */
    case object None extends SameSite

    /** Returns `Strict`, `Lax` and `None`, in this order. */
    def values: List[SameSite] = List(Strict, Lax, None)
  }
}
