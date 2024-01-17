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

package zio.http.endpoint

import java.time.Instant

import zio._
import zio.test._

import zio.stream.ZStream

import zio.schema.annotation.validate
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.ImageMetadata
import zio.http.endpoint._
import zio.http.forms.Fixtures.formField

object MultipartSpec extends ZIOHttpSpec {
  def spec = suite("MultipartSpec")(
    suite("multipart/form-data")(
      test("multiple outputs produce multipart response") {
        check(
          Gen.alphaNumericString,
          Gen.int(1, Int.MaxValue),
          Gen.int(1, Int.MaxValue),
          Gen.alphaNumericString,
          Gen.instant,
        ) { (title, width, height, description, createdAt) =>
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(GET / "test-form")
                .outCodec(
                  HttpCodec.contentStream[Byte]("image", MediaType.image.png) ++
                    HttpCodec.content[String]("title") ++
                    HttpCodec.content[Int]("width") ++
                    HttpCodec.content[Int]("height") ++
                    HttpCodec.content[ImageMetadata]("metadata"),
                )
                .implement {
                  Handler.succeed(
                    (
                      ZStream.fromChunk(bytes),
                      title,
                      width,
                      height,
                      ImageMetadata(description, createdAt),
                    ),
                  )
                }
            result   <- route.toHttpApp.runZIO(Request.get(URL.decode("/test-form").toOption.get)).exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            form     <- response.body.asMultipartForm.orDie
            mediaType = response.header(Header.ContentType).map(_.mediaType)
          } yield assertTrue(
            mediaType == Some(MediaType.multipart.`form-data`),
            form.formData.size == 5,
            form.formData.map(_.name).toSet == Set("image", "title", "width", "height", "metadata"),
            form.get("image").map(_.contentType) == Some(MediaType.image.png),
            form.get("image").map(_.asInstanceOf[FormField.Binary].data) == Some(bytes),
            form.get("title").map(_.asInstanceOf[FormField.Text].value) == Some(title),
            form.get("width").map(_.asInstanceOf[FormField.Text].value) == Some(width.toString),
            form.get("height").map(_.asInstanceOf[FormField.Text].value) == Some(height.toString),
            form.get("metadata").map(_.asInstanceOf[FormField.Binary].data) == Some(
              Chunk.fromArray(s"""{"description":"$description","createdAt":"$createdAt"}""".getBytes),
            ),
          )
        }
      },
      test("outputs without name get automatically generated names") {
        check(
          Gen.alphaNumericString,
          Gen.int(1, Int.MaxValue),
          Gen.int(1, Int.MaxValue),
          Gen.alphaNumericString,
          Gen.instant,
        ) { (title, width, height, description, createdAt) =>
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(GET / "test-form")
                .outCodec(
                  HttpCodec.contentStream[Byte](MediaType.image.png) ++
                    HttpCodec.content[String] ++
                    HttpCodec.content[Int] ++
                    HttpCodec.content[Int] ++
                    HttpCodec.content[ImageMetadata],
                )
                .implement {
                  Handler.succeed(
                    (
                      ZStream.fromChunk(bytes),
                      title,
                      width,
                      height,
                      ImageMetadata(description, createdAt),
                    ),
                  )
                }
            result   <- route.toHttpApp.runZIO(Request.get(URL.decode("/test-form").toOption.get)).exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            form     <- response.body.asMultipartForm.orDie
            mediaType = response.header(Header.ContentType).map(_.mediaType)
          } yield assertTrue(
            mediaType == Some(MediaType.multipart.`form-data`),
            form.formData.size == 5,
            form.formData.map(_.name).toSet == Set("field0", "field1", "field2", "field3", "field4"),
            form.get("field0").map(_.contentType) == Some(MediaType.image.png),
            form.get("field0").map(_.asInstanceOf[FormField.Binary].data) == Some(bytes),
            form.get("field1").map(_.asInstanceOf[FormField.Text].value) == Some(title),
            form.get("field2").map(_.asInstanceOf[FormField.Text].value) == Some(width.toString),
            form.get("field3").map(_.asInstanceOf[FormField.Text].value) == Some(height.toString),
            form.get("field4").map(_.asInstanceOf[FormField.Binary].data) == Some(
              Chunk.fromArray(s"""{"description":"$description","createdAt":"$createdAt"}""".getBytes),
            ),
          )
        }
      },
      test("multiple inputs got decoded from multipart/form-data body") {
        check(Gen.alphaNumericString, Gen.alphaNumericString, Gen.instant) { (title, description, createdAt) =>
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(POST / "test-form")
                .inStream[Byte]("uploaded-image", Doc.p("Image data"))
                .in[String]("title")
                .in[ImageMetadata]("metadata", Doc.p("Image metadata with description and creation date and time"))
                .out[(Long, String, ImageMetadata)]
                .implement {
                  Handler.fromFunctionZIO { case (stream, title, metadata) =>
                    stream.runCount.map(count => (count, title, metadata))
                  }
                }
            form  = Form(
              FormField.simpleField("title", title),
              FormField.binaryField(
                "metadata",
                Chunk.fromArray(
                  s"""{"description":"$description","createdAt":"$createdAt"}""".getBytes,
                ),
                MediaType.application.json,
              ),
              FormField.binaryField("uploaded-image", bytes, MediaType.image.png),
            )
            boundary <- Boundary.randomUUID
            result   <- route.toHttpApp
              .runZIO(
                Request.post(URL.decode("/test-form").toOption.get, Body.fromMultipartForm(form, boundary)),
              )
              .exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            result   <- response.body.asString.orDie
          } yield assertTrue(
            result == s"""[[1024,"$title"],{"description":"$description","createdAt":"$createdAt"}]""",
          )
        }
      },
      test("multipart input/output roundtrip check") {
        check(Gen.chunkOfBounded(2, 8)(formField)) { fields =>
          val endpoint =
            fields.foldLeft(
              Endpoint(POST / "test-form")
                .copy(output = HttpCodec.status(Status.Ok))
                .asInstanceOf[Endpoint[Any, Any, Any, Any, EndpointMiddleware.None]],
            ) { case (ep, (_, schema, name, isStreaming)) =>
              if (isStreaming)
                name match {
                  case Some(name) =>
                    ep.copy(
                      input = (ep.input ++ HttpCodec.contentStream(name)(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                      output = (ep.output ++ HttpCodec
                        .contentStream(name)(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                    )
                  case None       =>
                    ep.copy(
                      input = (ep.input ++ HttpCodec.contentStream(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                      output = (ep.output ++ HttpCodec.contentStream(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                    )
                }
              else
                name match {
                  case Some(name) =>
                    ep.copy(
                      input = (ep.input ++ HttpCodec.content(name)(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                      output = (ep.output ++ HttpCodec.content(name)(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                    )
                  case None       =>
                    ep.copy(
                      input = (ep.input ++ HttpCodec.content(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                      output = (ep.output ++ HttpCodec.content(schema))
                        .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                    )
                }
            }
          val route    =
            endpoint.implement(Handler.identity[Any])

          val form =
            Form(
              fields.map(_._1).zipWithIndex.map { case (field, idx) =>
                if (field.name.isEmpty) field.name(s"field$idx") else field
              },
            )

          for {
            boundary   <- Boundary.randomUUID
            result     <- route.toHttpApp
              .runZIO(
                Request.post(URL.decode("/test-form").toOption.get, Body.fromMultipartForm(form, boundary)),
              )
              .exit
            response   <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            resultForm <- response.body.asMultipartForm.orDie
            mediaType = response.header(Header.ContentType).map(_.mediaType)

            normalizedIn  <- ZIO.foreach(form.formData) { field =>
              field.asChunk.map(field.name -> _)
            }
            normalizedOut <- ZIO.foreach(resultForm.formData) { field =>
              field.asChunk.map(field.name -> _)
            }
          } yield assertTrue(
            mediaType == Some(MediaType.multipart.`form-data`),
            normalizedIn == normalizedOut,
          )
        }
      },
    ),
  )
}
