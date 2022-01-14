import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / watchAntiEntropy     := FiniteDuration(2000, TimeUnit.MILLISECONDS)
