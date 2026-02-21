package models

case class OAuthUser(
  googleId: String,
  email: String,
  name: Option[String],
  picture: Option[String],
  googleAccessToken: String,
  googleRefreshToken: Option[String],
  googleTokenExpiryAt: Long
)
