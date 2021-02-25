package zio.web

//import zio.{Has, Tag, Task, URIO, ZIO}
//import com.github.ghik.silencer.silent

//import scala.annotation.implicitNotFound

sealed trait Endpoints

object Endpoints {

  sealed case class ::[H <: Endpoint[_, _, _], T <: Endpoints] private[web] (head: H, tail: T) extends Endpoints
  sealed trait Empty extends Endpoints
  private[web] case object Empty extends Empty

  val empty: Empty = Empty

  implicit class EndpointsOps[A <: Endpoints](self: A) {
    def ::[H <: Endpoint[_, _, _]](head: H): H :: A = new ::(head, self)

    // def invoke[M, I, O](endpoint: Endpoint[M, I, O])(request: I)(implicit get: Lens.Get[A, Endpoint[M, I, O]], tagA: Tag[A])
    //   : ZIO[Has[ClientService[A]], Throwable, O]
    //   = {
    //     val _ = tagA
    //     ZIO.accessM[Has[ClientService[A]]](_.get[ClientService[A]].invoke(endpoint, request))
    //   }
  }

  trait ClientService[A <: Endpoints] {
    // def invoke[M, I, O](endpoint: Endpoint[M, I, O], request: I)(implicit get: Lens.Get[A, Endpoint[M, I, O]]): Task[O]
  }
}