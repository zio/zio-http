package zio.http.model.headers.values

sealed trait UpgradeInsecureRequests

/**
 * The HTTP Upgrade-Insecure-Requests request header sends a signal to the
 * server expressing the client's preference for an encrypted and authenticated
 * response.
 */
object UpgradeInsecureRequests {
  case object UpgradeInsecureRequests        extends UpgradeInsecureRequests
  case object InvalidUpgradeInsecureRequests extends UpgradeInsecureRequests

  def toUpgradeInsecureRequests(value: String): UpgradeInsecureRequests =
    if (value.trim == "1") UpgradeInsecureRequests
    else InvalidUpgradeInsecureRequests

  def fromUpgradeInsecureRequests(upgradeInsecureRequests: UpgradeInsecureRequests): String =
    upgradeInsecureRequests match {
      case UpgradeInsecureRequests        => "1"
      case InvalidUpgradeInsecureRequests => ""
    }
}
