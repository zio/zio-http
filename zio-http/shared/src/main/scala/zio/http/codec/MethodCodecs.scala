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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The method codecs that `MethodCodec` and `HttpCodec` provide.
 */
private[codec] trait MethodCodecs {
  import HttpCodecType.Method

  /**
   * Creates a codec for requests with the HTTP method `method`, which holds no
   * value. Decoding a request with another method gives an
   * `HttpCodecError.MalformedMethod` error.
   */
  def method(method: zio.http.Method): HttpCodec[HttpCodecType.Method, Unit] =
    HttpCodec.Method(SimpleCodec.Specified(method))

  /**
   * A codec that holds the HTTP method of the request as a `zio.http.Method`,
   * for any method.
   */
  val method: HttpCodec[HttpCodecType.Method, zio.http.Method] =
    HttpCodec.Method(SimpleCodec.Unspecified())

  /**
   * The codec for the `CONNECT` method; the same as `method(Method.CONNECT)`.
   */
  def connect: HttpCodec[Method, Unit] = method(zio.http.Method.CONNECT)

  /** The codec for the `DELETE` method; the same as `method(Method.DELETE)`. */
  def delete: HttpCodec[Method, Unit] = method(zio.http.Method.DELETE)

  /** The codec for the `HEAD` method; the same as `method(Method.HEAD)`. */
  def head: HttpCodec[Method, Unit] = method(zio.http.Method.HEAD)

  /** The codec for the `GET` method; the same as `method(Method.GET)`. */
  def get: HttpCodec[Method, Unit] = method(zio.http.Method.GET)

  /**
   * The codec for the `OPTIONS` method; the same as `method(Method.OPTIONS)`.
   */
  def options: HttpCodec[Method, Unit] = method(zio.http.Method.OPTIONS)

  /** The codec for the `PATCH` method; the same as `method(Method.PATCH)`. */
  def patch: HttpCodec[Method, Unit] = method(zio.http.Method.PATCH)

  /** The codec for the `POST` method; the same as `method(Method.POST)`. */
  def post: HttpCodec[Method, Unit] = method(zio.http.Method.POST)

  /** The codec for the `PUT` method; the same as `method(Method.PUT)`. */
  def put: HttpCodec[Method, Unit] = method(zio.http.Method.PUT)

  /** The codec for the `TRACE` method; the same as `method(Method.TRACE)`. */
  def trace: HttpCodec[Method, Unit] = method(zio.http.Method.TRACE)
}
