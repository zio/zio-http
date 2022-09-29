package zio.http.model.headers.values

import zio.Chunk
import zio.http.model.Method

import scala.annotation.tailrec

/**
 * The Allow header must be sent if the server responds with a 405 Method Not Allowed status code to indicate
 * which request methods can be used.
 */
final case class Allow(methods: Chunk[Method])

object Allow {

  def toAllow(value: String): Allow = {
    @tailrec def loop(index: Int, value: String, acc: Allow): Allow = {
      if (index == -1) acc.copy(methods = acc.methods ++ Chunk(Method.fromString(value.trim)))
      else {
        val valueChunk     = value.substring(0, index)
        val valueRemaining = value.substring(index + 1)
        val newIndex       = valueRemaining.indexOf(',')
        loop(
          newIndex,
          valueRemaining,
          acc.copy(methods = acc.methods ++ Chunk(Method.fromString(valueChunk.trim)))
        )
      }
    }
    loop(value.indexOf(','), value, Allow(Chunk.empty))
  }

  def fromAllow(allow: Allow): String = allow.methods.map(_.toString()).mkString(", ")

}
