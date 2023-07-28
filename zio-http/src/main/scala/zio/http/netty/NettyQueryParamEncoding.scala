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

package zio.http.netty

import java.nio.charset.Charset

import scala.jdk.CollectionConverters._

import zio.Chunk

import zio.http.QueryParams
import zio.http.internal.QueryParamEncoding

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

private[netty] object NettyQueryParamEncoding extends QueryParamEncoding {
  override final def decode(queryStringFragment: String, charset: Charset): QueryParams = {
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val decoder = new QueryStringDecoder(queryStringFragment, charset, false)
      val params  = decoder.parameters()
      QueryParams(params.asScala.view.map { case (k, v) =>
        (k, Chunk.fromIterable(v.asScala))
      }.toMap)
    }
  }

  override final def encode(baseUri: String, queryParams: QueryParams, charset: Charset): String = {
    val encoder = new QueryStringEncoder(baseUri, charset)
    queryParams.map.foreach { case (key, values) =>
      if (key != "") {
        if (values.isEmpty) {
          encoder.addParam(key, "")
        } else
          values.foreach(value => encoder.addParam(key, value))
      }
    }

    encoder.toString
  }
}
