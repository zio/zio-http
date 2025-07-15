package example.auth.webauthn
import zio._

// ============================================================================
// WebAuthn API Simulation
// ============================================================================

trait WebAuthnAPI {

  /**
   * Create a new credential
   */
  def create(options: PublicKeyCredentialCreationOptions): Task[PublicKeyCredential]

  /**
   * Get an existing credential for authentication
   */
  def get(options: PublicKeyCredentialRequestOptions): Task[PublicKeyCredential]

  /**
   * Check if user-verifying platform authenticator is available
   */
  def isUserVerifyingPlatformAuthenticatorAvailable(): Task[Boolean]
}

