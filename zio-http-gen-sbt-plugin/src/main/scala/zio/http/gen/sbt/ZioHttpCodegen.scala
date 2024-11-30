package zio.http.gen.sbt

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
import java.nio.file.{Files, Path}

import scala.io.Source

import zio.json.yaml._

import zio.schema.codec.JsonCodec

import zio.http.endpoint.openapi.OpenAPI
import zio.http.gen.openapi.{Config, EndpointGen}
import zio.http.gen.scala.CodeGen

import sbt.Defaults.configSrcSub
import sbt.Keys._
import sbt._
import sbt.util.FileInfo

object ZioHttpCodegen extends AutoPlugin {

  object autoImport {

    val ZIOpenApi = config("oapi") extend Compile

    val zioHttpCodegenMake    = taskKey[Seq[File]]("Generate ADTs & endpoints from OpenAPI spec files")
    val zioHttpCodegenConf    = settingKey[Config]("Configuration for codegen")
    val zioHttpCodegenSources = settingKey[File]("Source dir. analoguous to e.g: scalaSource or javaSource")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = inConfig(ZIOpenApi)(
    Seq[Setting[_]](
      zioHttpCodegenSources := (Compile / sourceDirectory).value / "oapi",
      sourceGenerators      := Nil,
      sourceManaged         := configSrcSub(sourceManaged).value,
      sourceDirectories     := List(zioHttpCodegenSources.value, sourceManaged.value),
      sources               := {
        val generatedFiles = Defaults.generate(sourceGenerators).value
        streams.value.log.info(s"Generated ${generatedFiles.length} OpenAPI spec files")
        sourceDirectories.value.flatMap(listFilesRec)
      },
      zioHttpCodegenConf    := Config.default,
      zioHttpCodegenMake    := Def.taskDyn {

        val maybeCached = (ZIOpenApi / zioHttpCodegenMake).previous
        val s           = streams.value

        val cachedCodegen = Tracked.inputChanged[
          FilesInfo[FileInfo.full.F],
          Def.Initialize[Task[Seq[File]]],
        ](s.cacheStoreFactory.make("zioapigen")) { (changed, in) =>
          maybeCached match {
            case Some(cached) if !changed =>
              Def.task {
                s.log.info("OpenAPI spec unchanged, skipping codegen and using cached files")
                cached
              }
            case _                        =>
              Def.task {
                s.log.info("OpenAPI spec changed, or nothing in cache: regenerating code")
                zioHttpCodegenMakeTask.value
              }
          }
        }

        cachedCodegen(FileInfo.full((ZIOpenApi / sources).value.toSet))
      }.value,
    ),
  ) ++ Seq(
    Compile / sourceGenerators += (ZIOpenApi / zioHttpCodegenMake).taskValue,
    Compile / watchSources ++= (ZIOpenApi / sources).value,
  )

  lazy val zioHttpCodegenMakeTask = Def.task {
    val openApiFiles: Seq[File]    = (ZIOpenApi / sources).value
    val openApiRootDirs: Seq[File] = (ZIOpenApi / sourceDirectories).value
    val baseDir                    = baseDirectory.value
    val targetDir: File            = (Compile / sourceManaged).value
    val config: Config             = (ZIOpenApi / zioHttpCodegenConf).value

    openApiFiles.flatMap { openApiFile =>
      val content        = fileContentAsString(openApiFile)
      val format         = Format.fromFileName(openApiFile.getName)
      val openApiRootDir = openApiRootDirs.foldLeft(baseDir) { case (bestSoFar, currentDir) =>
        val currentPath    = currentDir.getAbsolutePath
        val isAncestor     = openApiFile.getAbsolutePath.startsWith(currentPath)
        val isMoreSpecific = currentPath.length >= bestSoFar.getAbsolutePath.length
        if (isAncestor && isMoreSpecific) currentDir
        else bestSoFar
      }
      val parsedOrError  = format match {
        case Format.YAML => content.fromYaml[OpenAPI](JsonCodec.jsonDecoder(OpenAPI.schema))
        case Format.JSON => OpenAPI.fromJson(content)
      }

      parsedOrError match {
        case Left(error)    => throw new Exception(s"Failed to parse OpenAPI from $format: $error")
        case Right(openapi) =>
          val codegenEndpoints = EndpointGen.fromOpenAPI(openapi, config)
          val basePackageParts = dirDiffToPackage(openApiRootDir, openApiFile)
          val currentTargetDir = basePackageParts.foldLeft(targetDir)(_ / _)
          val currentTargetPat = Path.of(currentTargetDir.toURI())

          CodeGen
            .renderedFiles(codegenEndpoints, basePackageParts.mkString("."))
            .map { case (path, content) =>
              val filePath = currentTargetPat.resolve(path)
              Files.createDirectories(filePath.getParent)
              Files.write(filePath, content.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING)
              filePath.toFile
            }
            .toSeq

      }
    }
  }

  private def listFilesRec(dir: File): List[File] = {
    def inner(dir: File, acc: List[File]): List[File] =
      sbt.io.IO.listFiles(dir).foldRight(acc) { case (f, tail) =>
        if (f.isDirectory) inner(f, tail)
        else f :: tail
      }

    inner(dir, Nil)
  }

  private def fileContentAsString(file: File): String = {
    val s = Source.fromFile(file)
    val r = s.mkString
    s.close()
    r
  }

  private def dirDiffToPackage(dir: File, file: File): List[String] = {
    val dirPath      = dir.toPath
    val filePath     = file.toPath
    val relativePath = dirPath.relativize(filePath.getParent)
    relativePath.toString.split(File.separatorChar).toList
  }
}
