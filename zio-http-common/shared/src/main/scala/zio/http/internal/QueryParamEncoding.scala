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

package zio.http.internal

import zio.Chunk
import zio.http.QueryParams

import java.nio.charset.Charset

private[http] trait QueryParamEncoding {
  def decode(queryStringFragment: String, charset: Charset): QueryParams
  def encode(baseUri: String, queryParams: QueryParams, charset: Charset): String
}

private[http] object QueryParamEncoding {
  val default: QueryParamEncoding = new QueryParamEncoding {
    def decode(queryStringFragment: String, charset: Charset): QueryParams = {
      val urlDecoded = java.net.URLDecoder.decode(queryStringFragment, charset.name())
      val params     = urlDecoded.split("[&;]").map { param =>
        val splitted = param.split('=')
        val key      = splitted(0)
        val values   = splitted(1)
        key -> Chunk.fromArray(values.split(','))
      }
      QueryParams(params.toMap)
    }

    def encode(baseUri: String, queryParams: QueryParams, charset: Charset): String =
      queryParams.toString(charset)
  }
}
