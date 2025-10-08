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

package zio.http.datastar

import zio.http._
import zio.http.codec._
import zio.http.endpoint.openapi.OpenAPIGen
import zio.http.endpoint.openapi.OpenAPIGen.{AtomizedMetaCodecs, MetaCodec}
import zio.http.endpoint.{AuthType, Endpoint}

/**
 * Converts ZIO HTTP Endpoints to Datastar action expressions for use in
 * `data-on-*` attributes. Generates strings like `@get('/users/\$id')` from
 * typed endpoints. Path parameters become `$param` placeholders; GET requests
 * include query parameter placeholders. Headers and request bodies are not
 * rendered (Datastar handles these automatically).
 */
object DatastarAction {

  sealed trait Action extends Product with Serializable {
    def method: Method
    def url: String
    def render: String = {
      val verb = method match {
        case Method.GET    => "get"
        case Method.POST   => "post"
        case Method.PUT    => "put"
        case Method.PATCH  => "patch"
        case Method.DELETE => "delete"
        case other         => other.render.toLowerCase
      }
      s"@${verb}('${url}')"
    }
  }

  final case class SimpleAction(method: Method, url: String) extends Action

  def fromEndpoint[PathInput, Input, Err, Output, Auth <: AuthType](
    ep: Endpoint[PathInput, Input, Err, Output, Auth],
  ): Action = {
    val atomizedInput = AtomizedMetaCodecs.flatten(ep.input)
    val method        = extractMethod(ep, atomizedInput)
    val pathStr       = renderPath(ep.route.pathCodec)
    val fullUrl       =
      if (method == Method.GET) appendQuery(atomizedInput, pathStr)
      else pathStr

    SimpleAction(method, fullUrl)
  }

  def asString[PathInput, Input, Err, Output, Auth <: AuthType](
    ep: Endpoint[PathInput, Input, Err, Output, Auth],
  ): String = fromEndpoint(ep).render

  private[datastar] def extractMethod[PathInput, Input, Err, Output, Auth <: AuthType](
    ep: Endpoint[PathInput, Input, Err, Output, Auth],
    atomizedInput: AtomizedMetaCodecs,
  ): Method = {
    val m = ep.route.method
    if (m != Method.ANY) m else OpenAPIGen.method(atomizedInput.method)
  }

  private[datastar] def renderPath[A](pathCodec: PathCodec[A]): String =
    pathCodec.render("$", "")

  private[datastar] def appendQuery(atomizedInput: AtomizedMetaCodecs, basePath: String): String = {
    val queryParams = extractQueryParamNames(atomizedInput)
    if (queryParams.isEmpty) basePath
    else {
      val queryString = queryParams.sorted.map(name => s"$name=$$${name}").mkString("&")
      s"$basePath?$queryString"
    }
  }

  private def extractQueryParamNames(atomizedInput: AtomizedMetaCodecs): Seq[String] =
    atomizedInput.query.flatMap {
      case MetaCodec(HttpCodec.Query(codec, _), _) =>
        codec.recordFields.map { case (field, _) => field.name }
      case _                                       => Seq.empty
    }
}
