/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

import zio.blocks.chunk.Chunk
import zio.blocks.endpoint.{CodecKind, Endpoint, HttpCodec}
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.Schema
import zio.http.{Body, ContentType, Headers, Method, Request, Response, Status, URL, Version}
import zio.test.*

object EndpointCodecSpec extends ZIOSpecDefault {

  def spec = suite("EndpointCodec")(
    test("EndpointCodec exports codec bridge functions") {
      assertTrue(
        EndpointCodec.encodeResponse != null,
        EndpointCodec.decodeRequest != null,
        EndpointCodec.encodeRequestBody != null,
        EndpointCodec.decodeResponse != null,
      )
    },
    test("EndpointResultHandler.resultHandlerId exists") {
      val handler: EndpointResultHandler[EndpointResultHandler.Id] = EndpointResultHandler.resultHandlerId
      assertTrue(handler.run[Int](42) == 42)
    },
  )
}
