package controllers

import javax.inject.Inject
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{Security, SecurityComponents}

//@Singleton
class AuthController @Inject() (val controllerComponents: SecurityComponents) extends Security[CommonProfile]{
    /**
      * 安全选项一：
      *
      * 使用 Secure 为单个资源提供了安全配置，第一个参数是采用的认证机制（多选），可选项：
      *
      *   AnonymousClient 表示该资源允许匿名访问机制。 可选的Client机制都必须在 SecurityModule 中注册
      *   （参见：@Provides def provideConfig），以下是所有可选的 Client:
      *   DirectBasicAuthClient：为资源提供 base authentication 认证机制。
      *   FacebookClient
      *   IndirectBasicAuthClient
      *
      * 第二个参数(可选)是授权比对机制（多选），授权器（Authorizer）需要在 SecurityModule 中注册
      *
      * 第三个参数是 matchers。
      *
      * */
    def index = Secure(clients = "DirectBasicAuthClient", authorizers = "admin_authorizer") { implicit request =>
        val webContext = new PlayWebContext(request, controllerComponents.playSessionStore)
        val sessionId = playSessionStore.getOrCreateSessionId(webContext)
        val csrfToken = playSessionStore.get(webContext, Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
        Ok(views.html.auth(request.profiles, csrfToken, sessionId))
    }

    /**
      * 除此之外，Secure可以不提供任何参数，而使用 HttpFilters 来实现安全配置。下面这个资源的访问控制，参见 CustomHttpFilter 中的说明.
      * */
    def admin = Secure() {  implicit request =>
        val webContext = new PlayWebContext(request, controllerComponents.playSessionStore)
        val sessionId = playSessionStore.getOrCreateSessionId(webContext)
        val csrfToken = playSessionStore.get(webContext, Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
        Ok(views.html.auth(request.profiles, csrfToken, sessionId))
    }
}
