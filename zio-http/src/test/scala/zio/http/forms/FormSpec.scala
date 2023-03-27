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

package zio.http.forms

import java.nio.charset.StandardCharsets

import scala.annotation.nowarn

import zio._
import zio.test._

import zio.stream.{ZStream, ZStreamAspect}

import zio.http.forms.Fixtures._
import zio.http.model.Header.ContentTransferEncoding
import zio.http.model.MediaType

object FormSpec extends ZIOSpecDefault {

  val urlEncodedSuite =
    suite("application/x-www-form-urlencoded ")(
      test("encoding") {
        val form    = Form.fromStrings("name" -> "John", "age" -> "30")
        val encoded = form.encodeAsURLEncoded()
        assertTrue(
          encoded == "name=John&age=30",
          form.formData.size == 2,
        )
      },
      test("decoding") {
        val form = Form.fromURLEncoded("name=John&age=30", StandardCharsets.UTF_8)
        form.map { form =>
          assertTrue(
            form.get("age").get.valueAsString.get == "30",
            form.get("name").get.valueAsString.get == "John",
          )
        }
      },
    )

  @nowarn
  val multiFormSuite = suite("multipart/form-data")(
    test("encoding") {

      val form = Form(
        FormData.textField("csv-data", "foo,bar,baz", MediaType.text.csv),
        FormData.binaryField(
          "file",
          Chunk[Byte](0x50, 0x4e, 0x47),
          MediaType.image.png,
        ),
        FormData.binaryField(
          "corgi",
          Chunk.fromArray(base64Corgi.getBytes()),
          MediaType.image.png,
          Some(ContentTransferEncoding.Base64),
          Some("corgi.png"),
        ),
      )

      val (_, actualByteStream) = form.encodeAsMultipartBytes(rng = () => "AaB03x")

      for {
        form2       <- Form.fromMultipartBytes(multipartFormBytes2)
        actualBytes <- actualByteStream.runCollect
      } yield assertTrue(
        actualBytes == multipartFormBytes2,
        form2 == form,
      )
    },
    test("decoding") {
      val boundary = Boundary("AaB03x")

      for {
        form <- Form.fromMultipartBytes(multipartFormBytes1)
        encoding = form.encodeAsMultipartBytes(StandardCharsets.UTF_8, boundary)
        bytes <- encoding._2.runCollect
        (text: FormData.Text) :: (image1: FormData.Binary) :: (image2: FormData.Binary) :: Nil = form.formData.toList
      } yield assertTrue(
        bytes == multipartFormBytes1,
        form.formData.size == 3,
        text.name == "submit-name",
        text.value == "Larry",
        text.contentType == MediaType.text.plain,
        text.filename.isEmpty,
        image1.name == "files",
        image1.data == Chunk[Byte](0x50, 0x4e, 0x47),
        image1.contentType == MediaType.image.png,
        image1.transferEncoding.isEmpty,
        image1.filename.get == "file1.txt",
        image2.name == "corgi",
        image2.contentType == MediaType.image.png,
        image2.transferEncoding.get == ContentTransferEncoding.Base64,
        image2.data == Chunk.fromArray(base64Corgi.getBytes()),
      )
    },
    test("decoding 2") {
      Form.fromMultipartBytes(multipartFormBytes3).map { form =>
        assertTrue(
          form.get("file").get.filename.get == "test.jsonl",
          form.get("file").get.valueAsString.isEmpty,
          form.get("file").get.asInstanceOf[FormData.Binary].data.size == 69,
        )
      }

    },
  )

  val multiFormStreamingSuite: Spec[Any, Throwable] =
    suite("multipart/form-data streaming")(
      test("encoding") {

        val form = Form(
          FormData.textField("csv-data", "foo,bar,baz", MediaType.text.csv),
          FormData.streamingBinaryField(
            "file",
            ZStream.fromChunk(Chunk[Byte](0x50, 0x4e, 0x47)) @@ ZStreamAspect.rechunk(3),
            MediaType.image.png,
          ),
          FormData.streamingBinaryField(
            "corgi",
            ZStream.fromChunk(Chunk.fromArray(base64Corgi.getBytes())) @@ ZStreamAspect.rechunk(8),
            MediaType.image.png,
            Some(ContentTransferEncoding.Base64),
            Some("corgi.png"),
          ),
        )

        val (_, actualByteStream) = form.encodeAsMultipartBytes(rng = () => "AaB03x")

        for {
          form2         <- Form.fromMultipartBytes(multipartFormBytes2)
          actualBytes   <- actualByteStream.runCollect
          collectedForm <- form.collectAll
        } yield assertTrue(
          actualBytes == multipartFormBytes2,
          form2 == collectedForm,
        )
      },
      test("decoding") {
        val boundary = Boundary("AaB03x")

        val stream = ZStream.fromChunk(multipartFormBytes1) @@ ZStreamAspect.rechunk(4)
        val form   = StreamingForm(stream, boundary, StandardCharsets.UTF_8)

        form.data
          .mapZIOPar(1) {
            case sb: FormData.StreamingBinary =>
              sb.collect
            case other: FormData              =>
              ZIO.succeed(other)
          }
          .runCollect
          .map { formData =>
            val (text: FormData.Text) :: (image1: FormData.Binary) :: (image2: FormData.Binary) :: Nil = formData.toList
            assertTrue(
              formData.size == 3,
              text.name == "submit-name",
              text.value == "Larry",
              text.contentType == MediaType.text.plain,
              text.filename.isEmpty,
              image1.name == "files",
              image1.data == Chunk[Byte](0x50, 0x4e, 0x47),
              image1.contentType == MediaType.image.png,
              image1.transferEncoding.isEmpty,
              image1.filename.get == "file1.txt",
              image2.name == "corgi",
              image2.contentType == MediaType.image.png,
              image2.transferEncoding.get == ContentTransferEncoding.Base64,
              image2.data == Chunk.fromArray(base64Corgi.getBytes()),
            )
          }
      },
      test("decoding 2") {
        val boundary      = Boundary("X-INSOMNIA-BOUNDARY")
        val stream        = ZStream.fromChunk(multipartFormBytes3) @@ ZStreamAspect.rechunk(16)
        val streamingForm = StreamingForm(stream, boundary, StandardCharsets.UTF_8)
        streamingForm.collectAll.map { form =>
          val contents =
            new String(form.get("file").get.asInstanceOf[FormData.Binary].data.toArray, StandardCharsets.UTF_8)
          assertTrue(
            form.get("file").get.filename.get == "test.jsonl",
            form.get("file").get.valueAsString.isEmpty,
            form.get("file").get.asInstanceOf[FormData.Binary].data.size == 69,
            contents == """{"prompt": "<prompt text>", "completion": "<ideal generated text>"}""" + "\r\n",
          )
        }
      },
    )

  def spec = suite("FormSpec")(urlEncodedSuite, multiFormSuite, multiFormStreamingSuite)
}
