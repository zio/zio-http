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

import zio.test._

import zio.http.Header.ContentType
import zio.http.{MediaType, ZIOHttpSpec}

object ContentTypeSpec extends ZIOHttpSpec {

  override def spec = suite("ContentType header suite")(
    test("parsing of invalid ContentType values") {
      assertTrue(
        ContentType.parse("").isLeft,
        ContentType.parse("something").isLeft,
      )
    },
    test("parsing of valid ContentType values") {
      val charsetUtf8 = java.nio.charset.Charset.forName("utf-8")
      val boundary    = zio.http.Boundary("---------------------------974767299852498929531610575")
      def checkBoth(in: String, result: ContentType) =
        assertTrue(
          ContentType.parse(in).toOption.get == result,
        ) && assertTrue(
          ContentType.render(result) == in,
        )
      checkBoth("x-word/listing-text", ContentType(MediaType.parseCustomMediaType(s"x-word/listing-text").get)) &&
      checkBoth(MediaType.image.`png`.fullType, ContentType(MediaType.image.`png`)) &&
      checkBoth(
        s"${MediaType.text.`html`.fullType}; charset=${charsetUtf8.toString.toLowerCase}",
        ContentType(MediaType.text.`html`, None, Some(charsetUtf8)),
      ) &&
      checkBoth(
        s"${MediaType.text.`html`.fullType}; charset=${charsetUtf8.toString.toLowerCase}; boundary=${boundary.id}",
        ContentType(MediaType.text.`html`, Some(boundary), Some(charsetUtf8)),
      ) &&
      assertTrue(
        ContentType
          .parse(s"${MediaType.text.`html`.fullType};charset=${charsetUtf8.toString}")
          .toOption
          .get == ContentType(MediaType.text.`html`, None, Some(charsetUtf8)),
      ) && assertTrue(
        ContentType
          .parse(s"""${MediaType.text.`html`.fullType};charset="${charsetUtf8.toString}"""")
          .toOption
          .get == ContentType(MediaType.text.`html`, None, Some(charsetUtf8)),
      ) && assertTrue(
        ContentType
          .parse(s"""${MediaType.text.`html`.fullType};charset="${charsetUtf8.toString}"; version=1.0.0""")
          .toOption
          .get == ContentType(MediaType.text.`html`, None, Some(charsetUtf8)),
      ) && assertTrue(
        ContentType
          .parse(s"""${MediaType.text.`html`.fullType};charset="${charsetUtf8.toString}"; version="1.0.0"""")
          .toOption
          .get == ContentType(MediaType.text.`html`, None, Some(charsetUtf8)),
      ) && assertTrue(
        ContentType
          .parse(s"""${MediaType.text.`html`.fullType};    charset="${charsetUtf8.toString}"""")
          .toOption
          .get == ContentType(MediaType.text.`html`, None, Some(charsetUtf8)),
      )
    },
    test("parsing and encoding is symmetrical") {
      val results =
        MediaType.allMediaTypes.map(mediaType => ContentType.render(ContentType.parse(mediaType.fullType).toOption.get))
      assertTrue(MediaType.allMediaTypes.map(_.fullType) == results)
    },
  )

}
