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

import scala.quoted.*

trait UrlInterpolator {

  extension(inline sc: StringContext) {
    inline def url(inline args: Any*): URL = ${ UrlInterpolatorMacro.url('sc, 'args) }
  }

}

private[http] object UrlInterpolatorMacro {

  def url(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[URL] = {
    import quotes.reflect.*
    import report.*

    val ctx = sc.valueOrAbort
    val staticParts = ctx.parts

    val argExprs = args match {
      case Varargs(exprs) => exprs
      case _ => errorAndAbort(s"Unexpected arguments", args)
    }

    val result = if (argExprs.isEmpty) {
      URL.decode(staticParts.mkString) match {
        case Left(error) => errorAndAbort(s"Invalid URL: $error", sc)
        case Right(url) =>
          val uri = Expr(url.encode)
          if (url.isAbsolute) {
            '{ URL.fromAbsoluteURI(new java.net.URI($uri)).get }
          } else {
            '{ URL.fromRelativeURI(new java.net.URI($uri)).get }
          }
      }
    } else {
      val injectedPartExamples =
        argExprs.map { arg =>
          val typ = arg.asTerm.tpe.asType
          typ match {
            case '[String] =>
              "string"
            case '[Byte] =>
              "123"
            case '[Short] =>
              "1234"
            case '[Int] =>
                "1234"
            case '[Long] =>
                "1234"
            case '[Boolean] =>
                "true"
            case '[Float] =>
                "1.23"
            case '[Double] =>
                "1.23"
            case '[java.util.UUID] =>
                "123e4567-e89b-12d3-a456-426614174000"
            case _ =>
              errorAndAbort(s"Injected field ${arg.show} has an unsupported type", arg)
          }
        }

      val exampleParts = staticParts.zipAll(injectedPartExamples, "", "").flatMap { case (a, b) => List(a, b) }
      val example = exampleParts.mkString

      URL.decode(example) match {
        case Left(error) =>
          errorAndAbort(s"Invalid URL: $error", sc)
        case Right(url) =>
          val parts =
            staticParts.map { s => Expr(s) }
              .zipAll(argExprs, Expr(""), Expr(""))
              .flatMap { case (a, b) => List(a, b) }

          val concatenated =
            parts.foldLeft[Expr[String]](Expr("")) { case (acc, part) =>
              '{$acc + $part}
            }

          if (url.isAbsolute) {
            '{
              URL.fromAbsoluteURI(new java.net.URI($concatenated)).get
            }
          } else {
            '{
              URL.fromRelativeURI(new java.net.URI($concatenated)).get
            }
          }
      }
    }

    result
  }

}