package controllers

import javax.inject.Inject
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.profile.JwtGenerator
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.Configuration

import scala.collection.JavaConverters.asScalaBuffer

//@Singleton
class AuthController @Inject() (val controllerComponents: SecurityComponents,
                                val appConf: Configuration) extends Security[CommonProfile]{
    /********************************
      * 安全配置方式一：
      *
      * 使用 Secure 为单个资源提供了安全配置，第一个参数是采用的认证机制（多选），可选项：
      *
      *   AnonymousClient 表示该资源允许匿名访问机制。 可选的Client机制都必须在 SecurityModule 中注册
      *   （参见：@Provides def provideConfig），以下是所有可选的 Client:
      *   DirectBasicAuthClient：为资源提供 base authentication 认证机制。
      *   FacebookClient
      *   IndirectBasicAuthClient
      *   ...
      *
      * 第二个参数(可选)是授权比对机制（多选），授权器（Authorizer）需要在 SecurityModule 中注册
      *
      * 第三个参数是 matchers, 用于将该 URL 之下的某个子 URL 排除在认证之外（无需认证）。
      *
      * */
    def index = Secure(clients = "FormClient,DirectBasicAuthClient", authorizers = "admin_authorizer") { implicit request =>
        val webContext = new PlayWebContext(request, controllerComponents.playSessionStore)
        val sessionId = playSessionStore.getOrCreateSessionId(webContext)
        val csrfToken = playSessionStore.get(webContext, Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
        /**
          * 通过 Secure 修饰的 request 可以直接获得用户登录后的 profiles
          * */
        Ok(views.html.auth(request.profiles, csrfToken, sessionId))
    }

    /**
      * 安全配置方式二：
      *
      * 除此之外，还可以使用 HttpFilters 来实现安全配置。下面这个资源的访问控制，参见 CustomHttpFilter 中的说明.
      *
      * 更多的安全配置选项参考：https://github.com/pac4j/play-pac4j-scala-demo/blob/master/app/controllers/Application.scala
      * */
    def admin_info = Action {  implicit request =>
        val webContext = new PlayWebContext(request, controllerComponents.playSessionStore)
        val sessionId = playSessionStore.getOrCreateSessionId(webContext)
        val csrfToken = playSessionStore.get(webContext, Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
        /**
          * 在 application.conf 中定义安全，然后通过缺省接口获得的 request 无法直接获得 profiles, 需要从 ProfileManager 中提取。
          * */
        val profiles: List[CommonProfile]  = asScalaBuffer(new ProfileManager[CommonProfile](webContext).getAll(true)).toList
        Ok(views.html.auth(profiles, csrfToken, sessionId))
    }

    /**
      * 登录以上 index 和 admin 都需要通过表格登录(FormClient)
      * */
    def loginForm = Action { request =>
        val formClient = config.getClients.findClient("FormClient").asInstanceOf[FormClient]
        Ok(views.html.loginForm.render(formClient.getCallbackUrl))    // formClient.getCallbackUrl 获得 Clients 的第一个参数值
    }

    /**************************************
      * 认证机制：JWT
      *
      * 无论何种安全配置方式，pac4j 都支持多种认证机制，除了上面的 FormClient 和 DirectBasicAuthClient 外，还可以支持 JWT 机制
      *
      * 生成 JWT token 的 URL 本身通过 Form 或 Basic 机制来登录，登陆后返回登录用户的 JWT token。然后可以使用 HeaderClient 或
      * ParameterClient 机制来访问其他 URL. 参见 application.conf 中的配置.
      * */
    def jwtGenerate() = Secure("FormClient,DirectBasicAuthClient") { implicit request =>
        val generator = new JwtGenerator[CommonProfile]( new SecretSignatureConfiguration(
            appConf.get[String]("pac4j.security.jwt_secret")   // "pac4j.security.jwt_secret" 是一个 256 位长的密钥用于加密 jwt token
        ))

        val profiles = request.profiles
        if(profiles.nonEmpty)
            Ok(views.html.jwt.render(generator.generate(profiles.head)))
        else
            Ok(views.html.error500())
    }

    /**
      * JWT
      *
      * 下面这个资源受 JWT 认证机制保护，详见 application.conf 中的配置.
      * */
    def jwt_info = Action {  implicit request =>
        val webContext = new PlayWebContext(request, controllerComponents.playSessionStore)
        val sessionId = playSessionStore.getOrCreateSessionId(webContext)
        val csrfToken = playSessionStore.get(webContext, Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
        val profiles: List[CommonProfile]  = asScalaBuffer(new ProfileManager[CommonProfile](webContext).getAll(true)).toList
        Ok(views.html.auth(profiles, csrfToken, sessionId))
    }
}
