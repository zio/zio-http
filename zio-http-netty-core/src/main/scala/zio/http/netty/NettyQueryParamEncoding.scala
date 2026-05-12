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

import zio.http.{QueryParams, QueryParamsBuilder}

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

private[http] object NettyQueryParamEncoding {
  final def decode(queryStringFragment: String, charset: Charset): QueryParams = {
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val decoder = new QueryStringDecoder(queryStringFragment, charset, false)
      val params  = decoder.parameters()
      val builder = QueryParamsBuilder.make(params.size())
      params.asScala.foreach { case (key, values) =>
        values.asScala.foreach(v => builder.add(key, v))
      }
      builder.build()
    }
  }

  final def encode(baseUri: String, queryParams: QueryParams, charset: Charset): String = {
    val encoder = new QueryStringEncoder(baseUri, charset)
    queryParams.toMap.foreach { case (key, values) =>
      if (key != "") {
        if (values.isEmpty) {
          encoder.addParam(key, "")
        } else {
          var i = 0
          while (i < values.length) {
            encoder.addParam(key, values(i))
            i += 1
          }
        }
      }
    }

    encoder.toString
  }
}
