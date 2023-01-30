package zio.http.api

import zio.Chunk

sealed trait EndpointSpecs[-Ids] { self =>
  def + (spec: EndpointSpec[_, _, _]): EndpointSpecs[Ids with spec.type] = 
    self ++ EndpointSpecs(spec)

  def ++ [Ids2](that: EndpointSpecs[Ids2]): EndpointSpecs[Ids with Ids2] = 
    EndpointSpecs.Concat(self, that)

  def specs: Chunk[EndpointSpec[_, _, _]]
}
object EndpointSpecs {
  private case object Empty extends EndpointSpecs[Any] {
    def specs: Chunk[EndpointSpec[_, _, _]] = Chunk.empty
  }
  private case class Concat[L, R](left: EndpointSpecs[L], right: EndpointSpecs[R]) extends EndpointSpecs[L with R] {
    def specs: Chunk[EndpointSpec[_, _, _]] = left.specs ++ right.specs 
  }
  private case class Single[Id](spec: EndpointSpec[_, _, _]) extends EndpointSpecs[Id] {
    def specs: Chunk[EndpointSpec[_, _, _]] = Chunk(spec)
  }
  
  def apply(spec: EndpointSpec[_, _, _]): EndpointSpecs[spec.type] = 
    Single(spec)
}