package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable
import java.util.UUID
import scala.concurrent.duration._

case class StateNonceData(state: String, nonce: String, codeVerifier: String, timestamp: Long)

@Singleton
class StateNonceStore @Inject() (implicit ec: ExecutionContext) {

  // In-memory store (replace with Redis for production)
  private val store: mutable.Map[String, StateNonceData] = mutable.Map()
  private val ttl: Long                                  = 5 * 60 * 1000 // 5 minutes

  /**
   * Generate and store state/nonce pair
   */
  def generateAndStore(): StateNonceData = {
    val state        = UUID.randomUUID().toString
    val nonce        = UUID.randomUUID().toString
    val codeVerifier = generateCodeVerifier()
    val data         = StateNonceData(state, nonce, codeVerifier, System.currentTimeMillis())

    store.put(state, data)
    data
  }

  /**
   * Validate and retrieve state/nonce data
   */
  def validate(state: String): Option[StateNonceData] =
    store.get(state).flatMap { data =>
      val isExpired = System.currentTimeMillis() - data.timestamp > ttl
      if (isExpired) {
        store.remove(state)
        None
      } else {
        store.remove(state) // Consume state (one-time use)
        Some(data)
      }
    }

  /**
   * Generate PKCE code verifier (43-128 chars, unreserved characters)
   */
  private def generateCodeVerifier(): String = {
    val chars  = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val random = scala.util.Random
    (1 to 64).map(_ => chars(random.nextInt(chars.length))).mkString
  }

  /**
   * Generate PKCE code challenge from verifier (S256)
   */
  def generateCodeChallenge(codeVerifier: String): String = {
    import java.security.MessageDigest
    import java.util.Base64
    val bytes  = codeVerifier.getBytes("UTF-8")
    val digest = MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(hash)
  }
}
