package zio.http.cli

import zio._
import zio.cli._
import zio.http.Response

final case class Input[A](embed: (A, Response) => Response, options: Options[A])
final case class EndpointInput[A](input: Input[A])

final case class HeaderOutput()
final case class ContentOutput()

final case class EndpointOutput(headerOutputs: Chunk[HeaderOutput], contentOutput: ContentOutput)
