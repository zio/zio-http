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

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait UrlInterpolator {

  implicit class UrlInterpolatorHelper(val sc: StringContext) {
    def url(args: Any*): URL = macro UrlInterpolatorMacro.url
  }
}

private[http] object UrlInterpolatorMacro {
  def url(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[URL] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(p: String)) :: Nil))) =>
        val result = URL.decode(p) match {
          case Left(error) => c.abort(c.enclosingPosition, s"Invalid URL: ${error.getMessage}")
          case Right(url)  =>
            if (url.isAbsolute) {
              val uri = url.encode
              q"_root_.zio.http.URL.fromAbsoluteURI(new _root_.java.net.URI($uri)).get"
            } else {
              val uri = url.encode
              q"_root_.zio.http.URL.fromRelativeURI(new _root_.java.net.URI($uri)).get"
            }
        }
        c.Expr[URL](result)
      case Apply(_, List(Apply(_, staticPartLiterals)))                  =>
        val staticParts          = staticPartLiterals.map { case Literal(Constant(p: String)) => p }
        val injectedPartExamples =
          args.map { arg =>
            val typ = arg.actualType
            if (typ =:= c.typeOf[String]) {
              "string"
            } else if (typ =:= c.typeOf[Byte]) {
              "123"
            } else if (typ =:= c.typeOf[Short]) {
              "1234"
            } else if (typ =:= c.typeOf[Int]) {
              "1234"
            } else if (typ =:= c.typeOf[Long]) {
              "1234"
            } else if (typ =:= c.typeOf[Boolean]) {
              "true"
            } else if (typ =:= c.typeOf[Float]) {
              "1.23"
            } else if (typ =:= c.typeOf[Double]) {
              "1.23"
            } else if (typ =:= c.typeOf[java.util.UUID]) {
              "123e4567-e89b-12d3-a456-426614174000"
            } else {
              c.abort(c.enclosingPosition, s"Unsupported type in url interpolator: $typ")
            }
          }
        val exampleParts = staticParts.zipAll(injectedPartExamples, "", "").flatMap { case (a, b) => List(a, b) }
        val example      = exampleParts.mkString
        URL.decode(example) match {
          case Left(error) =>
            c.abort(c.enclosingPosition, s"Invalid URL: ${error.getMessage}")
          case Right(url)  =>
            val parts =
              staticParts.map { s => Literal(Constant(s)) }
                .zipAll(args.map(_.tree), Literal(Constant("")), Literal(Constant("")))
                .flatMap { case (a, b) => List(a, b) }

            val concatenated =
              parts.foldLeft[Tree](q"""""""") { case (acc, part) =>
                q"$acc + $part"
              }

            val result = if (url.isAbsolute) {
              q"_root_.zio.http.URL.fromAbsoluteURI(new _root_.java.net.URI($concatenated)).get"
            } else {
              q"_root_.zio.http.URL.fromRelativeURI(new _root_.java.net.URI($concatenated)).get"
            }

            c.Expr[URL](result)
        }
    }
  }
}
