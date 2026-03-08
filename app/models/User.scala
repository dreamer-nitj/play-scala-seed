package models

import java.time.Instant
import slick.jdbc.H2Profile.api._
import slick.lifted.{ProvenShape, Tag}
import play.api.libs.json.Json

// define the case class, The data model in scala
case class User(
  id: Int,
  username: String,
  password: Option[String],
  email: Option[String],
  emailVerified: Boolean = false,
  googleId: Option[String] = None,
  googleAccessToken: Option[String] = None,
  googleRefreshToken: Option[String] = None,
  googleTokenExpiresAt: Option[Long] = None,
  pictureUrl: Option[String] = None,
  oauthLinkedAt: Option[Instant] = None
)

// The database table definition for the User model using Slick
class Users(tag: slick.lifted.Tag) extends slick.jdbc.H2Profile.api.Table[User](tag, "users") {
  def id                   = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def username             = column[String]("username")
  def password             = column[Option[String]]("password")
  def email                = column[Option[String]]("email")
  def emailVerified        = column[Boolean]("email_verified")
  def googleId             = column[Option[String]]("google_id")
  def googleAccessToken    = column[Option[String]]("google_access_token")
  def googleRefreshToken   = column[Option[String]]("google_refresh_token")
  def googleTokenExpiresAt = column[Option[Long]]("google_token_expires_at")
  def pictureUrl           = column[Option[String]]("picture_url")
  def oauthLinkedAt        = column[Option[Instant]]("oauth_linked_at")

  def * = (
    id,
    username,
    password,
    email,
    emailVerified,
    googleId,
    googleAccessToken,
    googleRefreshToken,
    googleTokenExpiresAt,
    pictureUrl,
    oauthLinkedAt
  ) <> ((User.apply _).tupled, User.unapply)
}

case class UserUpdateRequest(
  id: Int,
  username: String,
  email: String
)

object UserUpdateRequest {
  implicit val format: play.api.libs.json.OFormat[UserUpdateRequest] = Json.format[UserUpdateRequest]
}
