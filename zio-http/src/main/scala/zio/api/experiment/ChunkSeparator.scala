package zhttp.api.experiment

import zhttp.api.experiment.ChunkSeparator.IndexedChunk
import zhttp.api.experiment.InputCodec.{Atom, InputBody}
import zio.Chunk

object ChunkSeparator {
  final case class Indexed[+A](value: A, index: Int)

  final case class IndexedChunk[+A](value: Chunk[Indexed[A]]) {
    def collect[B](pf: PartialFunction[A, B]): IndexedChunk[B] =
      IndexedChunk {
        value.collect { case Indexed(a, i) if pf.isDefinedAt(a) => Indexed(pf(a), i) }
      }

    def length: Int = value.length

    def toChunk: Chunk[A] = value.map(_.value)
  }

  object IndexedChunk {
    def fromChunk[A](chunk: Chunk[A]): IndexedChunk[A] = {
      val indexed = chunk.zipWithIndex.map { case (a, i) => Indexed(a, i) }
      IndexedChunk(indexed)
    }

    def makeReassembler[A](
      original: IndexedChunk[A],
      separated: Chunk[IndexedChunk[A]],
    ): Chunk[Chunk[Any]] => Chunk[Any] = {
      val length                            = original.length
      val movements: Array[(Int, Int, Int)] =
        separated.zipWithIndex.flatMap { case (chunk, chunkIndex) =>
          chunk.value.zipWithIndex.map { case (Indexed(_, index), itemIndex) =>
            (chunkIndex, itemIndex, index)
          }
        }.toArray

      results => {
        val array: Array[Any] = Array.ofDim(length)
        movements.foreach { case (chunkIndex, itemIndex, index) =>
          array(index) = results(chunkIndex)(itemIndex)
        }
        Chunk.fromArray(array)
      }
    }
  }

  sealed trait Event extends Product with Serializable

  object Event {
    final case class Create(value: String)  extends Event
    final case class Update(value: Int)     extends Event
    final case class Delete(value: Boolean) extends Event

  }
}

object ChunkExperiment extends App {
  import ChunkSeparator.Event._
  import ChunkSeparator._

  val exampleEvents: Chunk[Event] =
    Chunk(Create("a"), Update(1), Update(3), Delete(false), Create("b"), Update(5), Delete(true))

  val indexedEvents: IndexedChunk[Event] = IndexedChunk.fromChunk(exampleEvents)
  val createEvents: IndexedChunk[Create] = indexedEvents.collect { case c: Create => c }
  val updateEvents: IndexedChunk[Update] = indexedEvents.collect { case u: Update => u }
  val deleteEvents: IndexedChunk[Delete] = indexedEvents.collect { case d: Delete => d }

  val reassemble = IndexedChunk.makeReassembler(indexedEvents, Chunk(createEvents, updateEvents, deleteEvents))

  val result   = reassemble(Chunk(Chunk("a", "b"), Chunk(1, 3, 5), Chunk(false, true)))
  val expected = Chunk("a", 1, 3, false, "b", 5, true)
  println(result)
  println(result == expected)
}

final case class SeparatedAtoms(
  routeAtoms: Chunk[RouteParser[_]],
  headerAtoms: Chunk[HeaderParser[_]],
  queryAtoms: Chunk[QueryParser[_]],
  bodyAtoms: Chunk[InputBody[_]],
  assembleResult: Chunk[Chunk[Any]] => Chunk[Any],
)

object SeparatedAtoms {
  def make[Input](inputCodec: InputCodec[Input]): SeparatedAtoms = {
    val flattened      = IndexedChunk.fromChunk(InputCodec.flatten(inputCodec))
    val routeAtoms     = flattened.collect { case a: RouteParser[_] => a }
    val headerAtoms    = flattened.collect { case a: HeaderParser[_] => a }
    val queryAtoms     = flattened.collect { case a: QueryParser[_] => a }
    val bodyAtoms      = flattened.collect { case a: InputBody[_] => a }
    val assembleResult =
      IndexedChunk.makeReassembler(flattened, Chunk(routeAtoms, headerAtoms, queryAtoms, bodyAtoms))
    SeparatedAtoms(
      routeAtoms.toChunk,
      headerAtoms.toChunk,
      queryAtoms.toChunk,
      bodyAtoms.toChunk,
      assembleResult,
    )
  }
}
