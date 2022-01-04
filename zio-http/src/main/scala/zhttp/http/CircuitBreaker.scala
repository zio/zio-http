package zhttp.http

import java.util.concurrent.ConcurrentHashMap

object CircuitBreaker {

  sealed trait State
  case object Open       extends State
  case object Closed     extends State
  case object HalfOpen   extends State
  case object Disabled   extends State
  case object ForcedOpen extends State

  sealed trait SlidingWindowType
  case object COUNT_BASED extends SlidingWindowType
  // case object TIME_BASED  extends SlidingWindowType // unimplemented

  val registry = new ConcurrentHashMap[String, CircuitBreaker]()

  def instance(threshold: Thresholds): CircuitBreaker =
    registry.computeIfAbsent(threshold.name, _ => new CircuitBreaker(threshold))

}

class CircuitBreaker(threshold: Thresholds) {
  import CircuitBreaker._

  private var state: State     = Closed
  val responseQueue            = new scala.collection.mutable.Queue[Int]()
  var timeOfStateChanged: Long = currentTime

  private var countOfSuccess: Long = 0
  private var countOfError: Long   = 0

  def checkCurrentState: State = {
    resetState()
    state
  }

  def setState(state: State) = {
    this.state = state
    if (this.state == HalfOpen) {
      responseQueue.dequeueAll(_ => true)
      countOfSuccess = 0
      countOfError = 0
    }
    timeOfStateChanged = currentTime
  }

  def putHttpStatus(status: Int) = synchronized {
    responseQueue += status

    if (responseQueue.size > threshold.slidingWindowSize) {
      if (threshold.isError(responseQueue.dequeue())) {
        countOfError = countOfError - 1
      } else {
        countOfSuccess = countOfSuccess - 1
      }
    }

    if (threshold.isError(status)) {
      countOfError = countOfError + 1
    } else {
      countOfSuccess = countOfSuccess + 1
    }
  }

  private def resetState() = {
    state match {
      case Closed if withinErrorRatio()                                         => setState(Open)
      case Open if threshold.waitDurationInOpenState < elapsedTime()            => setState(HalfOpen)
      case HalfOpen if overWaitDurationInHalfOpenState()                        => setState(Open)
      case HalfOpen if withinPermittedCallCount() && overMinimumNumberOfCalls() =>
        if (withinErrorRatio()) {
          setState(Open)
        } else {
          setState(Closed)
        }
      case HalfOpen if withinPermittedCallCount() && overMinimumNumberOfCalls() =>
      case _                                                                    => // nothing to do
    }
  }

  def errorRatio(): Int = if (overMinimumNumberOfCalls()) {
    (countOfError * 100 / (countOfSuccess + countOfError)).toInt
  } else 0

  private def withinErrorRatio() = errorRatio() >= threshold.failureRateThreshold

  def withinPermittedCallCount() = threshold.permittedNumberOfCallsInHalfOpenState >= responseQueue.size

  private def overWaitDurationInHalfOpenState() =
    threshold.maxWaitDurationInHalfOpenState != 0 && threshold.maxWaitDurationInHalfOpenState < elapsedTime()

  private def overMinimumNumberOfCalls() = threshold.minimumNumberOfCalls <= countOfError + countOfSuccess

  private def currentTime = System.currentTimeMillis()

  private def elapsedTime() = currentTime - timeOfStateChanged
}

final case class Thresholds(
  name: String,
  failureRateThreshold: Int = 50,
  permittedNumberOfCallsInHalfOpenState: Int = 10,
  maxWaitDurationInHalfOpenState: Int = 0, // [ms]
  slidingWindowType: CircuitBreaker.SlidingWindowType = CircuitBreaker.COUNT_BASED,
  slidingWindowSize: Int = 100,            // [number(COUNT_BASED) or second(TIME_BASED)]
  minimumNumberOfCalls: Int = 100,
  waitDurationInOpenState: Long = 60000,   // [ms]
) {
  import CircuitBreaker._
  def isError(status: Int): Boolean = status == 408 || status >= 500

  def watchStatus[R1, E1](res: Response[R1, E1]): Response[R1, E1] = {
    instance(this).putHttpStatus(res.status.asJava.code())
    res
  }
}
