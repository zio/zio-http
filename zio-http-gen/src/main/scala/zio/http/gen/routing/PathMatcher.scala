package zio.http.gen.routing

import zio.{UIO, ZIO}

trait PathMatcher {
  def matches(path: String): Boolean
  def matchesZIO(path: String): UIO[Boolean]
}

object PathMatcher {
  private class StateMachinePathMatcher(rootState: State) extends PathMatcher {
    def matches(path: String): Boolean = {
      if (!rootState.possibleLengths.contains(path.length)) return false
      
      @scala.annotation.tailrec
      def go(idx: Int, current: State): Boolean = 
        if (idx == path.length) {
          current.isTerminal
        } else {
          current.transition(path.charAt(idx)) match {
            case Some(next) => go(idx + 1, next)
            case None => false
          }
        }
      
      go(0, rootState)
    }

    def matchesZIO(path: String): UIO[Boolean] =
      ZIO.succeed(matches(path))
  }

  def compile(paths: Set[String]): PathMatcher = {
    val state = State.compile(paths)
    new StateMachinePathMatcher(state)
  }

  def compileNormalized(paths: Set[String]): PathMatcher = {
    val normalizedPaths = paths.map { path =>
      if (path.isEmpty || path == "/") "/" 
      else if (path.endsWith("/")) path.dropRight(1)
      else path
    }
    compile(normalizedPaths)
  }
}