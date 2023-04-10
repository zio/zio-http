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

package zio.http.codec

private[codec] trait MethodCodecs {
  import HttpCodecType.Method

  def method(method: zio.http.Method): HttpCodec[HttpCodecType.Method, Unit] =
    HttpCodec.Method(SimpleCodec.Specified(method))

  val method: HttpCodec[HttpCodecType.Method, zio.http.Method] =
    HttpCodec.Method(SimpleCodec.Unspecified())

  def connect: HttpCodec[Method, Unit] = method(zio.http.Method.CONNECT)
  def delete: HttpCodec[Method, Unit]  = method(zio.http.Method.DELETE)
  def get: HttpCodec[Method, Unit]     = method(zio.http.Method.GET)
  def options: HttpCodec[Method, Unit] = method(zio.http.Method.OPTIONS)
  def post: HttpCodec[Method, Unit]    = method(zio.http.Method.POST)
  def put: HttpCodec[Method, Unit]     = method(zio.http.Method.PUT)
}
