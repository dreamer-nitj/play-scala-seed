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

case class LoginRequest(username: String, password: String)
case class LoginResponse(token: String, username: String)

@Singleton
class LoginController @Inject() (
  val controllerComponents: ControllerComponents,
  config: Configuration,
  userRepository: UserRepository,
  authAction: AuthAction
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
}
