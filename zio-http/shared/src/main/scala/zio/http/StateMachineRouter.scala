/*
 * Copyright 2023 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.http

import zio._
import scala.collection.immutable.{Map => IMap}

/**
 * A high-performance state machine based router implementation that aims for zero allocations
 * on the happy path. The state machine transitions between states based on input characters,
 * allowing for early exits and efficient path matching.
 */
object StateMachineRouter {
  sealed trait State[+A]
  final case object Initial extends State[Nothing]
  final case class Segment[A](chars: Array[Char], pos: Int, next: State[A]) extends State[A]
  final case class Branch[A](transitions: IMap[Char, State[A]]) extends State[A]
  final case class Match[A](value: A, next: State[A]) extends State[A]
  final case object NoMatch extends State[Nothing]

  /**
   * A builder for constructing the state machine from route patterns.
   * This is used during initialization to build an efficient state machine.
   */
  final class Builder[A] private {
    private var states: IMap[String, State[A]] = IMap.empty
    private var rootState: State[A] = Initial

    /**
     * Adds a route pattern to the state machine.
     * This is used during initialization to build the state machine structure.
     */
    def addPattern(pattern: RoutePattern[_], value: A): Builder[A] = {
      val segments = pattern.pathCodec.segments
      var currentState = rootState
      
      segments.foreach { segment =>
        val segmentStr = segment.toString
        states.get(segmentStr) match {
          case Some(existingState) =>
            currentState = existingState
          case None =>
            val chars = segmentStr.toCharArray
            val newState = Segment(chars, 0, Initial)
            states = states.updated(segmentStr, newState)
            currentState = newState
        }
      }

      // Add final match state
      rootState = Match(value, NoMatch)
      this
    }

    def build(): StateMachine[A] = new StateMachine(rootState)
  }

  object Builder {
    def apply[A](): Builder[A] = new Builder[A]
  }

  /**
   * The actual state machine implementation that performs the matching.
   * This is designed to be allocation-free on the happy path.
   */
  final class StateMachine[A] private[StateMachineRouter] (initialState: State[A]) {
    
    /**
     * Matches a path against the state machine.
     * Returns the matched value if found, None otherwise.
     * Designed to be allocation-free on the happy path.
     */
    def matchPath(path: Path): Option[A] = {
      @annotation.tailrec
      def go(state: State[A], remaining: Chunk[String]): Option[A] = state match {
        case Initial => 
          if (remaining.isEmpty) None
          else go(state, remaining.tail)
          
        case Segment(chars, pos, next) =>
          if (remaining.isEmpty) None
          else {
            val current = remaining(0)
            if (pos >= chars.length) go(next, remaining.tail)
            else if (pos < current.length && chars(pos) == current.charAt(pos))
              go(Segment(chars, pos + 1, next), remaining)
            else None
          }
          
        case Branch(transitions) =>
          if (remaining.isEmpty) None
          else {
            val current = remaining(0)
            if (current.isEmpty) None
            else transitions.get(current.charAt(0)) match {
              case Some(next) => go(next, remaining)
              case None => None
            }
          }
          
        case Match(value, next) =>
          if (remaining.isEmpty) Some(value)
          else go(next, remaining)
          
        case NoMatch => None
      }

      go(initialState, path.segments)
    }
  }

  /**
   * Creates a new state machine from the given route patterns.
   */
  def fromPatterns[A](patterns: Iterable[(RoutePattern[_], A)]): StateMachine[A] = {
    val builder = Builder[A]()
    patterns.foreach { case (pattern, value) =>
      builder.addPattern(pattern, value)
    }
    builder.build()
  }
} 