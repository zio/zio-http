package fs2020

import zio._
import zhttp.service._
import zhttp.http._
import zio.duration._
import zhttp.http.Middleware._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

//    _______ ___    _  _ _   _
//   |_  /_ _/ _ \  | || | |_| |_ _ __
//    / / | | (_) | | __ |  _|  _| '_ \
//   /___|___\___/  |_||_|\__|\__| .__/
//                               |_|

object FS2020 extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = ???
}
