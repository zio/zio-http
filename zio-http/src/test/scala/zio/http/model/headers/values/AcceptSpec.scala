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

package zio.http.model.headers.values

import zio.Chunk
import zio.test._

import zio.http.model.headers.values.Accept.{AcceptValue, InvalidAcceptValue, MediaTypeWithQFactor}
import zio.http.model.{MediaType, MimeDB}

object AcceptSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("Accept header suite")(
    test("parsing of invalid Accept values") {
      assertTrue(Accept.toAccept("") == InvalidAcceptValue) &&
      assertTrue(Accept.toAccept("something") == InvalidAcceptValue) &&
      assertTrue(Accept.toAccept("text/html;q=0.8, bla=q") == InvalidAcceptValue)
    },
    test("parsing of valid Accept values") {
      assertTrue(
        Accept.toAccept("text/html") == AcceptValue(Chunk(MediaTypeWithQFactor(text.`html`, None))),
      ) &&
      assertTrue(
        Accept.toAccept("text/html;q=0.8") ==
          AcceptValue(Chunk(MediaTypeWithQFactor(text.`html`.withQFactor(0.8), Some(0.8)))),
      ) &&
      assertTrue(
        Accept.toAccept("text/*") == AcceptValue(Chunk(MediaTypeWithQFactor(MediaType("text", "*"), None))),
      ) &&
      assertTrue(
        Accept.toAccept("*/*") == AcceptValue(Chunk(MediaTypeWithQFactor(MediaType("*", "*"), None))),
      ) &&
      assertTrue(
        Accept.toAccept("*/*;q=0.1") ==
          AcceptValue(Chunk(MediaTypeWithQFactor(MediaType("*", "*").withQFactor(0.1), Some(0.1)))),
      ) &&
      assertTrue(
        Accept.toAccept("text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8") ==
          AcceptValue(
            Chunk(
              MediaTypeWithQFactor(text.`html`, None),
              MediaTypeWithQFactor(application.`xhtml+xml`, None),
              MediaTypeWithQFactor(application.`xml`.withQFactor(0.9), Some(0.9)),
              MediaTypeWithQFactor(MediaType("*", "*").withQFactor(0.8), Some(0.8)),
            ),
          ),
      )
    },
    test("parsing and encoding is symmetrical") {
      val results = allMediaTypes.map(mediaType => Accept.fromAccept(Accept.toAccept(mediaType.fullType)))
      assertTrue(allMediaTypes.map(_.fullType) == results)
    },
  )

  implicit class MediaTypeTestOps(mediaType: MediaType) {
    def withQFactor(double: Double): MediaType = {
      mediaType
        .copy(parameters = Map("q" -> double.toString))
    }
  }
}
