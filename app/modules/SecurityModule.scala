package modules

import com.google.inject.{AbstractModule, Provides}
import org.pac4j.cas.client.{CasClient, CasProxyReceptor}
import org.pac4j.core.client.Clients
import org.pac4j.http.client.direct.{DirectBasicAuthClient, ParameterClient}
import org.pac4j.http.client.indirect.{FormClient, IndirectBasicAuthClient}
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator
import org.pac4j.oauth.client.{FacebookClient, TwitterClient}
import org.pac4j.oidc.client.OidcClient
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import java.io.File

import org.pac4j.cas.config.{CasConfiguration, CasProtocol}
import org.pac4j.play.store.{PlayCacheSessionStore, PlayCookieSessionStore, PlaySessionStore, ShiroAesDataEncrypter}
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
import org.pac4j.core.client.direct.AnonymousClient
import org.pac4j.core.config.Config
import org.pac4j.core.matching.PathMatcher
import org.pac4j.core.profile.CommonProfile
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.saml.client.SAML2Client
import org.pac4j.saml.config.SAML2Configuration
import security.{CustomAuthorizer, CustomizedHttpActionAdapter, UsernamePasswordAuthenticator}

/**
  * SecurityModule 是 play 负责安全的模块，通过在 application.conf 中注册来引入：
  *
  *   play.modules.enabled += "modules.SecurityModule"
  *
  * SecurityModule 基于 Guice 的 AbstractModule 来实现自动注入, 它相当于 Spring Boot 的 @Configuration
  */
class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {
    /**
      * 1）初始化 SecurityModule 的配置
      * */
    override def configure(): Unit = {
        /**
          * 1-1) 需要为 pac4j 指定一个 SessionStore, 可以使用 Redis 或其他实现，缺省选用 PlayCookieSessionStore，为此需要设置一个随机字符串,
          * 用于加密 cookie.
          * */
        val sKey = "rpkTGtoJvLIdsrPd"    // 取 16 位长
        val dataEncrypter = new ShiroAesDataEncrypter(sKey)
        val playSessionStore = new PlayCookieSessionStore(dataEncrypter)
        bind(classOf[PlaySessionStore]).toInstance(playSessionStore)

        /**
          * 1-2) 认证通过后，可以将包括 SessionStore，Akka ExecuteContext, matcher, clients, authorizor 等等许多上下文信息
          * 通过 SecurityComponents 传递给 controller（也可以不传）。
          *
          * 这里缺省使用 DefaultSecurityComponents 作为上下文容器。
          * */
        bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])

        /**
          * 1-3) 用户通过认证后需要一个 Profile 来存放认证信息，这个 profile 会被传递给 ProfileAuthorizer。
          * 缺省指定 CommonProfile 作为模版。
          * */
        bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

        /**
          * 1-4) 需要配置两个 endpoint。 其中一个为 indirect client 认证通过后的回调地址. 这个地址用来存放用户的认证状态。
          *
          * 同时要在 route 中注册:
          *   GET         /callback                                @org.pac4j.play.CallbackController.callback()
          *   POST        /callback                                @org.pac4j.play.CallbackController.callback()
          * */
        // callback
        val callbackController = new CallbackController()
        // indirect client 认证后的缺省回调 URL。可选，如果没设置则返回之前的页面。
        callbackController.setDefaultUrl("/?defaultUrlAfterOauthSignIn")
        // 支持多个 OAuth 身份
        callbackController.setMultiProfile(true)
        bind(classOf[CallbackController]).toInstance(callbackController)

        /**
          * 1-5) 第二个 endpoint 为 logout 服务地址（可选）
          *
          * 同时要在 route 中注册:
          *   GET         /logout                                  @org.pac4j.play.LogoutController.logout()
          * */
        // logout
        val logoutController = new LogoutController()
        logoutController.setDefaultUrl("/byebye")    // 登出后返回的缺省 URL。（这里定义返回 /，并给予 “byebye” 参数）
        bind(classOf[LogoutController]).toInstance(logoutController)
    }

    /**
      * 2）重点：以下方法智能构造一个配置（Config）实例
      *
      * "@Provides" 是 Google Guice 的注释，相当于 Spring Boot 的 "@Bean" 它可以将一个函数声明为智能构造函数。这个函数的结果
      * 是构造出一个 “Config” 实例。
      * */
    @Provides
    def provideConfig(facebookClient: FacebookClient,
                      twitterClient: TwitterClient,
                      formClient: FormClient,
                      indirectBasicAuthClient: IndirectBasicAuthClient,
                      casClient: CasClient,
                      saml2Client: SAML2Client,
                      oidcClient: OidcClient[OidcProfile, OidcConfiguration],
                      parameterClient: ParameterClient,
                      directBasicAuthClient: DirectBasicAuthClient): Config = {

        /**
          * 2-1) 设置支持的认证机制(Client)，支持的认证机制包括(并非需要全部)：
          *
          *    OAuth，SAML，CAS，OpenID Connect，HTTP，OpenID，Google App Engine，Kerberos (SPNEGO)
          *
          *  其中 HTTP client 通过实现 Authenticator 接口来实现：
          *
          *    LDAP，SQL，JWT，MongoDB，CouchDB，IP address，REST API
          *
          *  Authenticator 只有一个方法：validate(C credentials, WebContext context)
          *
          *  认证机制通过一个总的 Clients 注册，然后作为 Config 的构造参数传入系统。以下 Clients 的参数来自下面的各项 @Provides 实例。
          * */
        val clients = new Clients(
            "http://localhost:9000/callback",   /** "/callback" 只用于间接客户端（indirect client 有效）. */
            facebookClient,
            twitterClient,
            formClient,
            indirectBasicAuthClient,
            casClient,
            saml2Client,
            oidcClient,
            parameterClient,
            directBasicAuthClient,
            new AnonymousClient()
        )

        /**
          * 2-2) 根据认证机制 (Client) 生成 Config。
          *
          * 在 Controller 中为某个 URL 配置 Secure Annotation, 并指定 Client 来实现对单个 URL 的认证和授权。（参见 AuthController）
          * */
        val config = new Config(clients)

        /**
          * 2-3）Client 只实现了认证，还需要通过 Authorizer 对 URL 做授权比对(可选)。（参见 security/CustomHttpFilters）
          *
          * 如果不设置 Authorizer,则任何通过认证的用户都可以访问该资源。
          *
          * 授权器的第一个参数是授权器的名称，由 application.conf 的 pac4j.security.rules 配置项根据资源来选择使用哪个授权器，
          * 第二个参数设置访问该资源需要的权限。可以添加多个授权器。
          * */
        config.addAuthorizer("admin_authorizer", new RequireAnyRoleAuthorizer("ROLE_ADMIN"))    // 检查通过认证的用户是否具有 ROLE_ADMIN 授予
        config.addAuthorizer("custom_authorizer", new CustomAuthorizer)

        /**
          * 2-4) Matcher 用于将特定的 URL 映射 “排除” 在认证之外（允许 public 访问）
          *
          * 有三类 Matcher 可选：PathMatcher, HeaderMatcher, HttpMethodMatcher
          * */
        val matcher = new PathMatcher().excludeRegex("^/public\\.html$")     // 可以连续调用多个 exclude...() 方法
        config.addMatcher("excludedPath", matcher)

        /**
          * 2-4 Option) 设置 HTTP 错误的返回页, 比如 redirections, forbidden, unauthorized 等。
          * */
        config.setHttpActionAdapter(new CustomizedHttpActionAdapter())

        /** 2-5) 返回配置实例 */
        config
    }

    /**
      * 认证（Authentication）器：
      *
      * 1-1) HTTP Basic Authentication
      * */
    @Provides
    def directBasicAuthClient: DirectBasicAuthClient = new DirectBasicAuthClient(new UsernamePasswordAuthenticator)

    @Provides
    def twitterClient: TwitterClient = new TwitterClient("HVSQGAw2XmiwcKOTvZFbQ", "FSiO9G9VRR4KCuksky0kgGuo8gAVndYymr4Nl7qc8AA")

    /*@Provides
    def provideFacebookClient: FacebookClient = {
        val fbId = configuration.getOptional[String]("fbId").get
        val fbSecret = configuration.getOptional[String]("fbSecret").get
        new FacebookClient(fbId, fbSecret)
    }*/

    /*@Provides
    def provideFormClient: FormClient = new FormClient(baseUrl + "/loginForm", new SimpleTestUernamePasswordAuthenticator())*/

    /*@Provides
    def provideIndirectBasicAuthClient: IndirectBasicAuthClient = new IndirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator())*/

    /*@Provides
    def provideCasProxyReceptor: CasProxyReceptor = new CasProxyReceptor()*/

    /*@Provides
    def provideCasClient(casProxyReceptor: CasProxyReceptor) = {
        val casConfiguration = new CasConfiguration("https://casserverpac4j.herokuapp.com/login")
        //val casConfiguration = new CasConfiguration("http://localhost:8888/cas/login")
        casConfiguration.setProtocol(CasProtocol.CAS20)
        //casConfiguration.setProxyReceptor(casProxyReceptor)
        new CasClient(casConfiguration)
    }*/

    /*@Provides
    def provideSaml2Client: SAML2Client = {
        val cfg = new SAML2Configuration("resource:samlKeystore.jks", "pac4j-demo-passwd", "pac4j-demo-passwd", "resource:openidp-feide.xml")
        cfg.setMaximumAuthenticationLifetime(3600)
        cfg.setServiceProviderEntityId("urn:mace:saml:pac4j.org")
        cfg.setServiceProviderMetadataPath(new File("target", "sp-metadata.xml").getAbsolutePath)
        new SAML2Client(cfg)
    }*/

    /*@Provides
    def provideOidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
        val oidcConfiguration = new OidcConfiguration()
        oidcConfiguration.setClientId("343992089165-i1es0qvej18asl33mvlbeq750i3ko32k.apps.googleusercontent.com")
        oidcConfiguration.setSecret("unXK_RSCbCXLTic2JACTiAo9")
        oidcConfiguration.setDiscoveryURI("https://accounts.google.com/.well-known/openid-configuration")
        oidcConfiguration.addCustomParam("prompt", "consent")
        val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](oidcConfiguration)
        oidcClient.addAuthorizationGenerator(new RoleAdminAuthGenerator)
        oidcClient
    }*/

    /*@Provides
    def provideParameterClient: ParameterClient = {
        val jwtAuthenticator = new JwtAuthenticator()
        jwtAuthenticator.addSignatureConfiguration(new SecretSignatureConfiguration("12345678901234567890123456789012"))
        val parameterClient = new ParameterClient("token", jwtAuthenticator)
        parameterClient.setSupportGetRequest(true)
        parameterClient.setSupportPostRequest(false)
        parameterClient
    }*/
}