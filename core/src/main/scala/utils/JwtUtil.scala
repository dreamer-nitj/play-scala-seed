package utils

import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import java.util.Date
import javax.crypto.spec.SecretKeySpec
import scala.util.{Failure, Success, Try}
import io.jsonwebtoken.Claims

class JwtUtil(secret: String, expiryMinutes: Int) {
  private val key = new SecretKeySpec(secret.getBytes, 0, secret.getBytes.length, "HmacSHA256")

  def generateToken(userId: Int, username: String): String = {
    val now        = System.currentTimeMillis()
    val expiryTime = now + expiryMinutes * 60 * 1000

    Jwts
      .builder()
      .setSubject(userId.toString)
      .claim("username", username)
      .setIssuedAt(new Date(now))
      .setExpiration(new Date(expiryTime))
      .signWith(key, SignatureAlgorithm.HS256)
      .compact()
  }

  def validateToken(token: String): Try[Claims] =
    Try {
      Jwts
        .parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody
    }

  def extractUserId(claims: Claims): String   = claims.getSubject
  def extractUsername(claims: Claims): String = claims.get("username", classOf[String])
}
