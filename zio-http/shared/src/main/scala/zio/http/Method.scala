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

import zio.http.codec.PathCodec

/**
 * Represents an HTTP method, such as GET, PUT, POST, or DELETE.
 *
 * `Method.ANY` matches every method. `Method.CUSTOM` holds a method name that
 * has no predefined constant, and `Method.Methods` is a set of methods built
 * with `#|`.
 *
 * {{{
 * val get: Method  = Method.fromString("get")
 * val either       = Method.GET #| Method.POST
 * val matchesPost  = either.matches(Method.POST) // true
 * val matchesPut   = either.matches(Method.PUT)  // false
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/routing/routes/ Routes]]
 */
sealed trait Method { self: Product =>

  /**
   * A right-biased way of combining two methods. If either method is
   * `Method.ANY`, the other will be returned. Otherwise, the right method will
   * be returned. For example, `Method.GET ++ Method.POST` is `Method.POST`.
   */
  def ++(that: Method): Method =
    if (that == Method.ANY) self
    else that

  /**
   * Combines two methods into one that matches either of them. If either method
   * is `Method.ANY`, the result is `Method.ANY`. If both methods are equal, the
   * result is that method. Otherwise the result is a `Method.Methods` holding
   * both, and `Method.Methods` operands are merged into it.
   */
  def #|(that: Method): Method =
    if (self == Method.ANY || that == Method.ANY) Method.ANY
    else
      (self, that) match {
        case (Method.Methods(a), Method.Methods(b)) => Method.Methods(a ++ b)
        case (Method.Methods(a), _)                 => Method.Methods(a + that)
        case (_, Method.Methods(b))                 => Method.Methods(b + self)
        case _ if self == that                      => self
        case _                                      => Method.Methods(Set(self, that))
      }

  /**
   * Returns a `RoutePattern` that matches this method followed by the path
   * described by `that`. With `PathCodec.empty`, the pattern has the method and
   * no path segments.
   */
  def /[A](that: PathCodec[A]): RoutePattern[A] =
    if (that == PathCodec.empty) RoutePattern.fromMethod(self).asInstanceOf[RoutePattern[A]]
    else RoutePattern.fromMethod(self) / that

  /**
   * Returns `true` when this method and `that` match. `Method.ANY` matches
   * every method, a `Method.Methods` matches when any of its members matches,
   * and any other pair matches when the two methods are equal (a
   * `Method.CUSTOM` name is compared case-sensitively).
   */
  def matches(that: Method): Boolean =
    if (self == Method.ANY) true
    else if (that == Method.ANY) true
    else
      self match {
        case Method.Methods(methods) => methods.exists(_.matches(that))
        case _                       =>
          that match {
            case Method.Methods(methods) => methods.exists(_.matches(self))
            case _                       => self == that
          }
      }

  /**
   * The name of the method, as it appears in the HTTP request. `Method.ANY` has
   * the name `GET`, and a `Method.Methods` has the names of its members sorted
   * and joined with `#|`.
   */
  val name: String

  /**
   * Returns the name of the method, or `*` for `Method.ANY`.
   */
  def render: String = if (self == Method.ANY) "*" else self.name

  override def toString: String = render
}

/**
 * The predefined methods, `Method.ANY`, and conversion from a method name.
 */
object Method {
  private[http] val standardMethods: Set[Method] =
    Set(OPTIONS, GET, HEAD, POST, PUT, PATCH, DELETE, TRACE, CONNECT)

  /**
   * Returns the predefined method whose name equals `method` ignoring case, or
   * a `Method.CUSTOM` with the given string unchanged when there is none. The
   * strings `ANY` and `*` and the empty string give a `Method.CUSTOM`, not
   * `Method.ANY`.
   */
  def fromString(method: String): Method =
    method.toUpperCase match {
      case POST.name    => Method.POST
      case GET.name     => Method.GET
      case OPTIONS.name => Method.OPTIONS
      case HEAD.name    => Method.HEAD
      case PUT.name     => Method.PUT
      case PATCH.name   => Method.PATCH
      case DELETE.name  => Method.DELETE
      case TRACE.name   => Method.TRACE
      case CONNECT.name => Method.CONNECT
      case _            => Method.CUSTOM(method)
    }

  /** A method that has no predefined constant, such as `PROPFIND`. */
  final case class CUSTOM(name: String) extends Method

  /** A set of methods, built by combining methods with `#|`. */
  final case class Methods(methods: Set[Method]) extends Method {

    /** The names of the members, sorted and joined with `#|`. */
    val name: String = methods.toList.sortBy(_.render).map(_.render).mkString("#|")
  }

  /** The `OPTIONS` method. */
  case object OPTIONS extends Method { val name = "OPTIONS" }

  /** The `GET` method. */
  case object GET extends Method { val name = "GET" }

  /** The `HEAD` method. */
  case object HEAD extends Method { val name = "HEAD" }

  /** The `POST` method. */
  case object POST extends Method { val name = "POST" }

  /** The `PUT` method. */
  case object PUT extends Method { val name = "PUT" }

  /** The `PATCH` method. */
  case object PATCH extends Method { val name = "PATCH" }

  /** The `DELETE` method. */
  case object DELETE extends Method { val name = "DELETE" }

  /** The `TRACE` method. */
  case object TRACE extends Method { val name = "TRACE" }

  /** The `CONNECT` method. */
  case object CONNECT extends Method { val name = "CONNECT" }

  /**
   * A method that matches every method. Its `name` is `GET`, and `render` gives
   * `*`.
   */
  case object ANY extends Method { val name = "GET" }
}
