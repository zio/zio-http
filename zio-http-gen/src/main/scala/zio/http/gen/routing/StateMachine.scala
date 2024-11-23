package zio.http.gen.routing

private[routing] sealed trait State {
  def transition(char: Char): Option[State]
  def isTerminal: Boolean
  def possibleLengths: Set[Int]
}

private[routing] object State {
  case class Node(
      transitions: Map[Char, State],
      remainingLengths: Set[Int],
      isTerminal: Boolean
  ) extends State {
    def transition(char: Char): Option[State] = transitions.get(char)
    def possibleLengths: Set[Int] = remainingLengths
  }

  def compile(paths: Set[String]): State = {
    def buildTransitions(paths: Set[String], depth: Int): Node = {
      val transitions = paths
        .filter(_.length > depth)
        .groupBy(_.charAt(depth))
        .map { case (c, suffixes) =>
          c -> buildTransitions(suffixes, depth + 1)
        }

      val remainingLengths = paths.map(_.length - depth).filter(_ >= 0)
      
      Node(
        transitions = transitions,
        remainingLengths = remainingLengths,
        isTerminal = paths.exists(_.length == depth)
      )
    }

    buildTransitions(paths, 0)
  }
}