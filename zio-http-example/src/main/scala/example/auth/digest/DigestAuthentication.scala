package example.auth.digest

import zio.http.Method

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom

object DigestAuthentication {

  def generateNonce(): String = {
    val bytes = new Array[Byte](16)
    ThreadLocalRandom.current().nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  def md5Hash(input: String): String = MessageDigest
    .getInstance("MD5")
    .digest(input.getBytes(StandardCharsets.UTF_8))
    .map("%02x".format(_))
    .mkString

  def sha256Hash(input: String): String = {
    MessageDigest
      .getInstance("SHA-256")
      .digest(
        input.getBytes(StandardCharsets.UTF_8),
      )
      .map("%02x".format(_))
      .mkString
  }

  private def computeHash(input: String, algorithm: String): String =
    algorithm.toLowerCase match {
      case "md5" | "md5-sess"         => md5Hash(input)
      case "sha-256" | "sha-256-sess" => sha256Hash(input)
      case _                          => md5Hash(input) // Default to MD5 for unknown algorithms
    }

  def digestResponse(
    username: String,
    realm: String,
    uri: URI,
    algorithm: String = "MD5",
    qop: String = "auth",
    cnonce: String,
    nonce: String,
    nc: Int,
    userhash: Boolean = false,
    password: String,
    method: Method = Method.GET,
  ): String = {
    // Compute HA1 (hash of username:realm:password)
    val a1 = if (userhash) {
      // If userhash is true, username field should contain H(username:realm)
      // So A1 = H(username:realm):password
      s"$username:$password"
    } else {
      // Normal case: A1 = username:realm:password
      s"$username:$realm:$password"
    }

    val ha1 = algorithm.toLowerCase match {
      case alg if alg.endsWith("-sess") =>
        // For session algorithms, HA1 = H(H(username:realm:password):nonce:cnonce)
        val baseHash = computeHash(a1, algorithm)
        computeHash(s"$baseHash:$nonce:$cnonce", algorithm)
      case _                            =>
        computeHash(a1, algorithm)
    }

    // Compute HA2 (hash of method:uri)
    val a2  = s"$method:${uri.toString}"
    val ha2 = computeHash(a2, algorithm)

    // Compute response based on qop
    val response = qop.toLowerCase match {
      case "auth" | "auth-int" =>
        // Format: H(HA1:nonce:nc:cnonce:qop:HA2)
        val ncHex          = "%08x".format(nc)
        val responseString = s"$ha1:$nonce:$ncHex:$cnonce:$qop:$ha2"
        computeHash(responseString, algorithm)
      case _                   =>
        // Legacy format: H(HA1:nonce:HA2)
        val responseString = s"$ha1:$nonce:$ha2"
        computeHash(responseString, algorithm)
    }
    response
  }

  def validateDigest(
    response: String,
    username: String,
    realm: String,
    uri: URI,
    algorithm: String,
    qop: String,
    cnonce: String,
    nonce: String,
    nc: Int,
    userhash: Boolean,
    password: String,
    method: Method = Method.GET,
  ): Boolean = {
    val expectedResponse =
      digestResponse(
        username = username,
        realm = realm,
        uri = uri,
        algorithm = algorithm,
        qop = qop,
        cnonce = cnonce,
        nonce = nonce,
        nc = nc,
        userhash = userhash,
        password = password,
        method = method,
      )

    expectedResponse.equals(response)
  }


}
