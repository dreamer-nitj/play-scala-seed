package services

import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.Configuration
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.util.Base64
import scala.util.{Failure, Success, Try}
import com.fasterxml.jackson.annotation.JsonProperty

case class GoogleTokenResponse(
  @JsonProperty("access_token") accessToken: String,
  @JsonProperty("expires_in") expiresIn: Int,
  @JsonProperty("refresh_token") refreshToken: Option[String],
  @JsonProperty("id_token") idToken: String,
  @JsonProperty("token_type") tokenType: String
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
  email_verified: Boolean,
  name: Option[String],
  nonce: String,
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

  implicit val googleTokenResponseFormat: OFormat[GoogleTokenResponse] = new OFormat[GoogleTokenResponse] {
    def reads(json: JsValue): JsResult[GoogleTokenResponse] = {
      for {
        accessToken <- (json \ "access_token").validate[String]
        expiresIn <- (json \ "expires_in").validate[Int]
        refreshToken <- (json \ "refresh_token").validateOpt[String]
        idToken <- (json \ "id_token").validate[String]
        tokenType <- (json \ "token_type").validate[String]
      } yield GoogleTokenResponse(accessToken, expiresIn, refreshToken, idToken, tokenType)
    }

    def writes(o: GoogleTokenResponse): JsObject = Json.obj(
      "access_token" -> o.accessToken,
      "expires_in" -> o.expiresIn,
      "refresh_token" -> o.refreshToken,
      "id_token" -> o.idToken,
      "token_type" -> o.tokenType
    )
  }
  
  implicit val googleUserInfoFormat: OFormat[GoogleUserInfo]           = Json.format[GoogleUserInfo]
  
  implicit val oAuthClaimsFormat: OFormat[OAuthClaims] = new OFormat[OAuthClaims] {
    def reads(json: JsValue): JsResult[OAuthClaims] = {
      for {
        sub <- (json \ "sub").validate[String]
        email <- (json \ "email").validate[String]
        emailVerified <- (json \ "email_verified").validate[Boolean]
        name <- (json \ "name").validateOpt[String]
        nonce <- (json \ "nonce").validate[String]
        iat <- (json \ "iat").validate[Long]
        exp <- (json \ "exp").validate[Long]
      } yield OAuthClaims(sub, email, emailVerified, name, nonce, iat, exp)
    }

    def writes(o: OAuthClaims): JsObject = Json.obj(
      "sub" -> o.sub,
      "email" -> o.email,
      "email_verified" -> o.email_verified,
      "name" -> o.name,
      "nonce" -> o.nonce,
      "iat" -> o.iat,
      "exp" -> o.exp
    )
  }

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
  def exchangeCodeForToken(code: String, codeVerifier: String): Future[Try[GoogleTokenResponse]] = {
    val params = s"grant_type=authorization_code&code=$code&client_id=$clientId&client_secret=$clientSecret&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}&code_verifier=$codeVerifier"
    
    ws.url(tokenUri)
      .withHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .post(params)
      .map { response =>
        println(s"[OAuth2Service] Token exchange response status: ${response.status}")
        println(s"[OAuth2Service] Token exchange response body: ${response.body}")
        if (response.status == 200) {
          Try(response.json.as[GoogleTokenResponse])
        } else {
          Failure(new Exception(s"Token exchange failed: ${response.status} ${response.statusText} - ${response.body}"))
        }
      }
      .recover { case ex: Exception =>
        println(s"[OAuth2Service] Token exchange error: ${ex.getMessage}")
        ex.printStackTrace()
        Failure(new Exception(s"Token exchange error: ${ex.getMessage}", ex))
      }
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
      println(s"[OAuth2Service] ID Token payload: $decoded")
      
      val claims  = Json.parse(decoded).as[OAuthClaims]
      println(s"[OAuth2Service] Parsed claims: $claims")

      // Validate nonce
      if (claims.nonce != nonce) {
        println(s"[OAuth2Service] Nonce mismatch: expected=$nonce, got=${claims.nonce}")
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
  def refreshAccessToken(refreshToken: String): Future[Try[GoogleTokenResponse]] = {
    val params = s"grant_type=refresh_token&refresh_token=$refreshToken&client_id=$clientId&client_secret=$clientSecret"
    
    ws.url(tokenUri)
      .withHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .post(params)
      .map { response =>
        if (response.status == 200) {
          Try(response.json.as[GoogleTokenResponse])
        } else {
          Failure(new Exception(s"Token refresh failed: ${response.status} ${response.statusText} - ${response.body}"))
        }
      }
      .recover { case ex: Exception =>
        Failure(new Exception(s"Token refresh error: ${ex.getMessage}", ex))
      }
  }
}
