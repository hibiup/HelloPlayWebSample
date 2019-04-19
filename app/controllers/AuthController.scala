package controllers

import javax.inject.Inject
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{Security, SecurityComponents}

class AuthController @Inject() (val controllerComponents: SecurityComponents) extends Security[CommonProfile]{
    /**
      * Secure 是有关这个资源的安全配置，表示本资源允许 "AnonymousClient" 和 "csrfToken" 用户机制访问.
      *
      *   AnonymousClient 缺省的匿名用户机制, 需要在 SecurityModule.provideConfig() 方法中注册该机制。
      *
      * */
    def index = Secure("AnonymousClient", "csrfToken") {
        implicit request =>
            val webContext = new PlayWebContext(request, playSessionStore)
            val sessionStore = webContext.getSessionStore().asInstanceOf[SessionStore[PlayWebContext]]
            val sessionId = sessionStore.getOrCreateSessionId(webContext)
            val csrfToken = sessionStore.get(webContext, Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
            Ok(views.html.auth(profiles, csrfToken, sessionId))
    }
}
