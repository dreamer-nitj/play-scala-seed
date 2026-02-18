package models

import slick.jdbc.H2Profile.api._
import slick.lifted.{ProvenShape, Tag}
import play.api.libs.json.Json


// define the case class, The data model in scala
case class User(id: Int, name: String, email: String)

// The database table definition for the User model using Slick
class Users(tag: slick.lifted.Tag) extends slick.jdbc.H2Profile.api.Table[User](tag, "USERS") {
  def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
  def name = column[String]("NAME")
  def email = column[String]("EMAIL")

  def * = (id, name, email) <> (User.tupled, User.unapply)
}

case class UserUpdateRequest(
  id: Int,
  name: String,
  email: String
)

object UserUpdateRequest {
  implicit val format: play.api.libs.json.OFormat[UserUpdateRequest] = Json.format[UserUpdateRequest]
}