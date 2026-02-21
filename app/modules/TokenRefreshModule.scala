package modules

import com.google.inject.AbstractModule
import play.api.inject.{Binding, Module}
import services.GoogleTokenRefresher
import play.api.Environment
import play.api.Configuration

class TokenRefreshModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(bind[GoogleTokenRefresher].toSelf.eagerly())
}
