package fs2020

import zio.{App, ExitCode, URIO}

object HelloWorld extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = ???
}
