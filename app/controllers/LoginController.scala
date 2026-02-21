package controllers

import play.api.mvc.ControllerComponents
import play.api.Configuration
import dao.UserRepository
import scala.concurrent.{ExecutionContext, Future}
import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.OFormat
import actions.AuthenticatedRequest
import actions.AuthAction
import services.{AccountLinker, OAuth2Service, PendingLinkStore, StateNonceStore}

case class LoginRequest(username: String, password: String)
case class LoginResponse(token: String, username: String)

@Singleton
class LoginController @Inject() (
  val controllerComponents: ControllerComponents,
  config: Configuration,
  userRepository: UserRepository,
  authAction: AuthAction,
  oauth2Service: OAuth2Service,
  stateNonceStore: StateNonceStore,
  accountLinker: AccountLinker,
  pendingLinkStore: PendingLinkStore
)(implicit ec: ExecutionContext)
    extends BaseController {

  private val jwtUtil = new utils.JwtUtil(
    config.get[String]("jwt.secret"),
    config.get[Int]("jwt.expiry-minutes")
  )

  implicit val loginRequestFormat: OFormat[LoginRequest]   = Json.format[LoginRequest]
  implicit val loginResponseFormat: OFormat[LoginResponse] = Json.format[LoginResponse]

  private def isValidCredentials(username: String, password: String): Boolean =
    // Replace with your actual authentication logic
    username == "admin" && password == "password123"

  def login() = Action.async(parse.json) { implicit request: Request[JsValue] =>
    try {
      val loginReq = request.body.as[LoginRequest]

      // validate credentials (hardcoded for demo, can be replaced with DB lookup)
      if (isValidCredentials(loginReq.username, loginReq.password)) {
        val userId = 1
        val token  = jwtUtil.generateToken(userId, loginReq.username)
        Future.successful(Ok(Json.toJson(LoginResponse(token, loginReq.username))))
      } else {
        Future.successful(Unauthorized(Json.obj("error" -> "Invalid credentials")))
      }
    } catch {
      case ex: Exception =>
        Future.successful(BadRequest(Json.obj("error" -> "Invalid request format")))
    }
  }

  def refreshToken() = authAction.async { implicit request: AuthenticatedRequest[AnyContent] =>
    // generate a new token with the same user info
    val newToken = jwtUtil.generateToken(request.userId.toInt, request.username)
    Future.successful(Ok(Json.toJson(LoginResponse(newToken, request.username))))
  }

  /** Initiate Google OAuth login */
  def googleLogin() = Action { implicit request: Request[AnyContent] =>
    val stateData     = stateNonceStore.generateAndStore()
    val codeChallenge = stateNonceStore.generateCodeChallenge(stateData.codeVerifier)
    val url           = oauth2Service.getAuthorizationUrl(stateData.state, stateData.nonce, codeChallenge)
    Redirect(url)
  }

  /** Google OAuth callback */
  def googleCallback() = Action.async { implicit request: Request[AnyContent] =>
    val codeOpt  = request.getQueryString("code")
    val stateOpt = request.getQueryString("state")
    val errorOpt = request.getQueryString("error")

    if (errorOpt.isDefined) {
      Future.successful(Redirect("/login?error=oauth_denied"))
    } else {
      (codeOpt, stateOpt) match {
        case (Some(code), Some(state)) =>
          stateNonceStore.validate(state) match {
            case None =>
              Future.successful(Redirect("/login?error=invalid_state"))
            case Some(stateData) =>
              oauth2Service.exchangeCodeForToken(code, stateData.codeVerifier).flatMap {
                case Failure(_) =>
                  Future.successful(Redirect("/login?error=token_exchange_failed"))
                case Success(tokenResp) =>
                  oauth2Service.validateIdToken(tokenResp.idToken, stateData.nonce) match {
                    case Failure(_) =>
                      Future.successful(Redirect("/login?error=invalid_id_token"))
                    case Success(claims) if !claims.emailVerified =>
                      Future.successful(Redirect("/login?error=email_not_verified"))
                    case Success(claims) =>
                      val expiresAt = (System.currentTimeMillis() / 1000) + tokenResp.expiresIn

                      // 1) Google ID already linked
                      userRepository.findByGoogleId(claims.sub).flatMap {
                        case Some(user) =>
                          userRepository.updateGoogleToken(user.id, tokenResp.accessToken, expiresAt).map { _ =>
                            val token = jwtUtil.generateToken(user.id, user.username)
                            Ok(Json.toJson(LoginResponse(token, user.username)))
                          }

                        case None =>
                          // 2) Email exists but not linked -> require confirmation
                          userRepository.findByEmail(claims.email).map {
                            case Some(_) =>
                              val linkToken = pendingLinkStore.create(
                                googleId = claims.sub,
                                email = claims.email,
                                accessToken = tokenResp.accessToken,
                                refreshToken = tokenResp.refreshToken,
                                expiresAt = expiresAt,
                                pictureUrl = None
                              )
                              Conflict(
                                Json.obj(
                                  "linkRequired" -> true,
                                  "linkToken"    -> linkToken,
                                  "email"        -> claims.email,
                                  "message"      -> "Please login with password to confirm linking."
                                )
                              )

                            case None =>
                              // 3) No user found -> create new OAuth user
                              accountLinker
                                .createOAuthUser(
                                  googleId = claims.sub,
                                  googleEmail = claims.email,
                                  googleName = claims.name,
                                  googleAccessToken = tokenResp.accessToken,
                                  googleRefreshToken = tokenResp.refreshToken,
                                  googleTokenExpiresAt = expiresAt,
                                  pictureUrl = None
                                )
                                .map {
                                  case Success(resp) =>
                                    val userId   = resp.userId.getOrElse(0)
                                    val username = claims.email.split("@")(0)
                                    val token    = jwtUtil.generateToken(userId, username)
                                    Ok(Json.toJson(LoginResponse(token, username)))

                                  case Failure(_) =>
                                    Redirect("/login?error=oauth_user_create_failed")
                                }
                          }
                      }
                  }
              }
          }
        case _ =>
          Future.successful(Redirect("/login?error=missing_code_or_state"))
      }
    }
  }

  /** Confirm account linking (requires username/password JWT) */
  def linkGoogleAccount() = authAction.async(parse.json) { implicit request: AuthenticatedRequest[JsValue] =>
    val linkReq = request.body.as[LinkAccountRequest]

    pendingLinkStore.consume(linkReq.linkToken) match {
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "Invalid or expired link token")))
      case Some(pending) =>
        accountLinker
          .linkAccount(
            userId = request.userId.toInt,
            googleId = pending.googleId,
            googleEmail = pending.email,
            googleAccessToken = pending.accessToken,
            googleRefreshToken = pending.refreshToken,
            googleTokenExpiresAt = pending.expiresAt,
            pictureUrl = pending.pictureUrl
          )
          .map {
            case Success(_) =>
              val token = jwtUtil.generateToken(request.userId.toInt, request.username)
              Ok(Json.toJson(LoginResponse(token, request.username)))
            case Failure(ex) =>
              Unauthorized(Json.obj("error" -> ex.getMessage))
          }
    }
  }
}
