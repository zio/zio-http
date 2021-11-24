package zhttp.internal

import zhttp.http._
import zio._

trait Api[R, E] { dApp: DynamicHttpApp[R, E] =>
  def app: Http[R, E, Request, Response[R, E]] = Http
    .collectM[Request] { case _ -> !! / "it" / id / _ =>
      dApp.get(id).map(app => app)
    }
    .flatten
}

case class DynamicHttpApp[R, E](ref: Ref[Map[String, HttpApp[R, E]]]) {
  self => // TODO String for now. but should be UUID
  def get(id: String): ZIO[R, E, HttpApp[R, E]] = ???
  def set(app: HttpApp[R, E]): String           = ???
}
