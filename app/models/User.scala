package models

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
class Users(tag: slick.lifted.Tag) extends slick.jdbc.H2Profile.api.Table[User](tag, "USERS") {
  def id       = column[Int]("ID", O.PrimaryKey, O.AutoInc)
  def username = column[String]("USERNAME")
  def email    = column[String]("EMAIL")

  def * = (id, username, email) <> (User.tupled, User.unapply)
}

case class UserUpdateRequest(
  id: Int,
  username: String,
  email: String
)

object UserUpdateRequest {
  implicit val format: play.api.libs.json.OFormat[UserUpdateRequest] = Json.format[UserUpdateRequest]
}
