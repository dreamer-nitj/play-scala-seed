package services

import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.Configuration
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.util.Base64
import scala.util.{Failure, Success, Try}

case class GoogleTokenResponse(
  accessToken: String,
  expiresIn: Int,
  refreshToken: Option[String],
  idToken: String,
  tokenType: String
)

case class GoogleUserInfo(
  sub: String,
  email: String,
  emailVerified: Boolean,
  name: Option[String],
  picture: Option[String]
)

case class OAuthClaims(
  sub: String,
  email: String,
  emailVerified: Boolean,
  name: Option[String],
  iat: Long,
  exp: Long
)

@Singleton
class OAuth2Service @Inject() (
  config: Configuration,
  ws: WSClient
)(implicit ec: ExecutionContext) {

  private val clientId     = config.get[String]("google.oauth.client-id")
  private val clientSecret = config.get[String]("google.oauth.client-secret")
  private val redirectUri  = config.get[String]("google.oauth.redirect-uri")
  private val tokenUri     = config.get[String]("google.oauth.token-uri")
  private val userInfoUri  = config.get[String]("google.oauth.userinfo-uri")
  private val scopes       = config.get[String]("google.oauth.scopes")

  implicit val googleTokenResponseFormat: OFormat[GoogleTokenResponse] = Json.format[GoogleTokenResponse]
  implicit val googleUserInfoFormat: OFormat[GoogleUserInfo]           = Json.format[GoogleUserInfo]
  implicit val oAuthClaimsFormat: OFormat[OAuthClaims]                 = Json.format[OAuthClaims]

  /** Generate Google authorization URL with PKCE
    */
  def getAuthorizationUrl(state: String, nonce: String, codeChallenge: String): String = {
    val params = Map(
      "client_id"             -> clientId,
      "redirect_uri"          -> redirectUri,
      "response_type"         -> "code",
      "scope"                 -> scopes,
      "state"                 -> state,
      "nonce"                 -> nonce,
      "code_challenge"        -> codeChallenge,
      "code_challenge_method" -> "S256",
      "access_type"           -> "offline", // Request refresh token
      "prompt"                -> "consent"  // Force consent screen
    )
    val queryString = params.map { case (k, v) => s"$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }.mkString("&")
    s"${config.get[String]("google.oauth.authorization-uri")}?$queryString"
  }

  /** Exchange authorization code for tokens
    */
  def exchangeCodeForToken(code: String, codeVerifier: String): Future[Try[GoogleTokenResponse]] =
    ws.url(tokenUri)
      .post(
        Map(
          "grant_type"    -> "authorization_code",
          "code"          -> code,
          "client_id"     -> clientId,
          "client_secret" -> clientSecret,
          "redirect_uri"  -> redirectUri,
          "code_verifier" -> codeVerifier
        )
      )
      .map { response =>
        if (response.status == 200) {
          Success(response.json.as[GoogleTokenResponse])
        } else {
          Failure(new Exception(s"Token exchange failed: ${response.statusText}"))
        }
      }
      .recover { case ex: Exception =>
        Failure(new Exception(s"Token exchange error: ${ex.getMessage}", ex))
      }

  /** Validate and decode ID token (JWT) Note: In production, verify signature with Google's public keys
    */
  def validateIdToken(idToken: String, nonce: String): Try[OAuthClaims] =
    Try {
      val parts = idToken.split("\\.")
      if (parts.length != 3) {
        throw new Exception("Invalid JWT format")
      }

      // Decode payload (add padding if needed)
      val payload = parts(1)
      val padded  = payload + "=" * (4 - payload.length % 4)
      val decoded = new String(Base64.getUrlDecoder.decode(padded))
      val claims  = Json.parse(decoded).as[OAuthClaims]

      // Validate nonce
      if ((Json.parse(decoded) \ "nonce").asOpt[String] != Some(nonce)) {
        throw new Exception("Nonce mismatch - possible replay attack")
      }

      // Validate expiration
      val currentTime = System.currentTimeMillis() / 1000
      if (claims.exp < currentTime) {
        throw new Exception("ID token expired")
      }

      claims
    }

  /** Fetch user info from Google using access token
    */
  def getUserInfo(accessToken: String): Future[Try[GoogleUserInfo]] =
    ws.url(userInfoUri)
      .withHttpHeaders("Authorization" -> s"Bearer $accessToken")
      .get()
      .map { response =>
        if (response.status == 200) {
          Success(response.json.as[GoogleUserInfo])
        } else {
          Failure(new Exception(s"Failed to fetch user info: ${response.statusText}"))
        }
      }
      .recover { case ex: Exception =>
        Failure(new Exception(s"User info fetch error: ${ex.getMessage}", ex))
      }

  /** Refresh access token using refresh token
    */
  def refreshAccessToken(refreshToken: String): Future[Try[GoogleTokenResponse]] =
    ws.url(tokenUri)
      .post(
        Map(
          "grant_type"    -> "refresh_token",
          "refresh_token" -> refreshToken,
          "client_id"     -> clientId,
          "client_secret" -> clientSecret
        )
      )
      .map { response =>
        if (response.status == 200) {
          Success(response.json.as[GoogleTokenResponse])
        } else {
          Failure(new Exception(s"Token refresh failed: ${response.statusText}"))
        }
      }
      .recover { case ex: Exception =>
        Failure(new Exception(s"Token refresh error: ${ex.getMessage}", ex))
      }
}
