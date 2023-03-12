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

package zio.http.model

import io.netty.handler.codec.http.HttpMethod
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait Method { self =>
  lazy val toJava: HttpMethod = Method.asHttpMethod(self)

  val text: String = toJava.asciiName().toString()

  override def toString: String = Method.asHttpMethod(self).name()
}

object Method {
  def fromHttpMethod(method: HttpMethod): Method =
    method match {
      case HttpMethod.OPTIONS => OPTIONS
      case HttpMethod.GET     => GET
      case HttpMethod.HEAD    => HEAD
      case HttpMethod.POST    => POST
      case HttpMethod.PUT     => PUT
      case HttpMethod.PATCH   => PATCH
      case HttpMethod.DELETE  => DELETE
      case HttpMethod.TRACE   => TRACE
      case HttpMethod.CONNECT => CONNECT
      case method             => CUSTOM(method.name())
    }

  def fromString(method: String): Method =
    method.toUpperCase match {
      case "POST"    => Method.POST
      case "GET"     => Method.GET
      case "OPTIONS" => Method.OPTIONS
      case "HEAD"    => Method.HEAD
      case "PUT"     => Method.PUT
      case "PATCH"   => Method.PATCH
      case "DELETE"  => Method.DELETE
      case "TRACE"   => Method.TRACE
      case "CONNECT" => Method.CONNECT
      case x         => Method.CUSTOM(x)
    }

  private[zio] def asHttpMethod(self: Method): HttpMethod = self match {
    case OPTIONS      => HttpMethod.OPTIONS
    case GET          => HttpMethod.GET
    case HEAD         => HttpMethod.HEAD
    case POST         => HttpMethod.POST
    case PUT          => HttpMethod.PUT
    case PATCH        => HttpMethod.PATCH
    case DELETE       => HttpMethod.DELETE
    case TRACE        => HttpMethod.TRACE
    case CONNECT      => HttpMethod.CONNECT
    case CUSTOM(name) => new HttpMethod(name)
  }

  final case class CUSTOM(name: String) extends Method

  object OPTIONS extends Method
  object GET     extends Method
  object HEAD    extends Method
  object POST    extends Method
  object PUT     extends Method
  object PATCH   extends Method
  object DELETE  extends Method
  object TRACE   extends Method
  object CONNECT extends Method

}
