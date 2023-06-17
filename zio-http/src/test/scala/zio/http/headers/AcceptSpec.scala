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

package zio.http.headers

import zio.NonEmptyChunk
import zio.test._

import zio.http.Header.Accept
import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http.MediaType

object AcceptSpec extends ZIOSpecDefault {
  import MediaType._

  override def spec = suite("Accept header suite")(
    test("parsing of invalid Accept values") {
      assertTrue(
        Accept.parse("").isLeft,
        Accept.parse("something").isLeft,
        Accept.parse("text/html;q=0.8, bla=q").isLeft,
      )
    },
    test("parsing of valid Accept values") {
      assertTrue(
        Accept.parse("text/html") == Right(Accept(NonEmptyChunk(MediaTypeWithQFactor(text.`html`, None)))),
        Accept.parse("text/html;q=0.8") ==
          Right(Accept(NonEmptyChunk(MediaTypeWithQFactor(text.`html`.qFactor(0.8), Some(0.8))))),
        Accept
          .parse("text/*") == Right(Accept(NonEmptyChunk(MediaTypeWithQFactor(MediaType("text", "*"), None)))),
        Accept.parse("*/*") == Right(Accept(NonEmptyChunk(MediaTypeWithQFactor(MediaType("*", "*"), None)))),
        Accept.parse("*/*;q=0.1") ==
          Right(Accept(NonEmptyChunk(MediaTypeWithQFactor(MediaType("*", "*").qFactor(0.1), Some(0.1))))),
        Accept.parse("text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8") ==
          Right(
            Accept(
              NonEmptyChunk(
                MediaTypeWithQFactor(text.`html`, None),
                MediaTypeWithQFactor(application.`xhtml+xml`, None),
                MediaTypeWithQFactor(application.`xml`.qFactor(0.9), Some(0.9)),
                MediaTypeWithQFactor(MediaType("*", "*").qFactor(0.8), Some(0.8)),
              ),
            ),
          ),
      )
    },
    test("parsing and encoding is symmetrical") {
      val results = allMediaTypes.map(mediaType => Accept.render(Accept.parse(mediaType.fullType).toOption.get))
      assertTrue(allMediaTypes.map(_.fullType) == results)
    },
  )

  implicit class MediaTypeTestOps(mediaType: MediaType) {
    def qFactor(double: Double): MediaType = {
      mediaType
        .copy(parameters = Map("q" -> double.toString))
    }
  }
}
