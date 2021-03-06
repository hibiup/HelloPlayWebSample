package modules

import com.google.inject.{AbstractModule, Provides}
import org.pac4j.cas.client.{CasClient, CasProxyReceptor}
import org.pac4j.core.client.{BaseClient, Clients}
import org.pac4j.http.client.direct.{DirectBasicAuthClient, HeaderClient, ParameterClient}
import org.pac4j.http.client.indirect.{FormClient, IndirectBasicAuthClient}
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator
import org.pac4j.oauth.client.{FacebookClient, TwitterClient}
import org.pac4j.oidc.client.OidcClient
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import org.pac4j.play.store.{PlayCookieSessionStore, PlaySessionStore, ShiroAesDataEncrypter}
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
import security.{CustomAuthorizer, CustomizedHttpActionAdapter, UsernamePasswordAuthenticator}

/**
  * SecurityModule 是 play 负责安全的模块，通过在 application.conf 中注册来引入：
  *
  *   play.modules.enabled += "modules.SecurityModule"
  *
  * SecurityModule 基于 Guice 的 AbstractModule 来实现自动注入, 它相当于 Spring Boot 的 @Configuration
  */
class SecurityModule(environment: Environment, appConf: Configuration) extends AbstractModule {
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
          *      参见 FormClient 的说明。
          * */
        // callback
        val callbackController = new CallbackController()
        // indirect client 认证后的缺省回调 URL。可选，如果没设置则返回之前的页面。
        callbackController.setDefaultUrl("/?afterlogin")
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
        logoutController.setDefaultUrl("/loginForm")    // 登出后返回的缺省 URL。（这里定义返回 /，并给予 “byebye” 参数）
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
                      parameterClient: ParameterClient,
                      headerClient: HeaderClient,
                      casClient: CasClient,
                      saml2Client: SAML2Client,
                      oidcClient: OidcClient[OidcProfile, OidcConfiguration],
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
          *
          *  第一个参数是 indirect client 的回调地址，参考 FormClient 的说明
          * */
        val clients = new Clients(
            "/login",
            directBasicAuthClient,
            formClient,
            headerClient,
            parameterClient,
            /*
            facebookClient,
            twitterClient,
            casClient,
            saml2Client,
            oidcClient,*/
            //indirectBasicAuthClient,
            new AnonymousClient()
        )

        /**
          * 2-2) 根据认证机制 (Client) 生成 Config。
          *
          * 在 Controller 中为某个 URL 配置 Secure Annotation, 并指定 Client 来实现对单个 URL 的认证和授权。（参见 AuthController）
          * */
        val config = new Config(clients)

        /**
          * 2-3）Client 只实现了认证机制，还需要通过 Authorizer 对 URL 做授权比对(可选)。（参见 security/CustomHttpFilters）
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

    /*****************************************
      * 认证机制：
      *
      * (*) HTTP Basic Authentication 机制：
      * */
    @Provides
    def directBasicAuthClient: DirectBasicAuthClient = new DirectBasicAuthClient(new UsernamePasswordAuthenticator)

    /**
      * (*) FormClient 机制：
      *
      * 表格登录需要一个 callback 地址，这个 callback 是登录表单的提交（POST）地址，会在 AuthController.loginForm 中被读取，
      * 然后赋给登录表单。需要在 Clients 中登记，并注册到 route 中：
      *
      *   GET     /login                   @org.pac4j.play.CallbackController.callback()
      *   POST    /login                   @org.pac4j.play.CallbackController.callback()
      * */
    @Provides
    def formClient: FormClient = new FormClient("/loginForm", new UsernamePasswordAuthenticator())

    /*@Provides
    def indirectBasicAuthClient: IndirectBasicAuthClient = new IndirectBasicAuthClient(new UsernamePasswordAuthenticator())*/

    /**
      * (*) HeaderClient 或 ParameterClient 机制允许我们通过 Header 或 URL 参数传递认证信息(比如 jwt token。jwt 本身不是一个独立
      * 的认证机制，它依托于 HeaderClient 或 ParameterClient 机制)。如果是 HeaderClient，通过 Header 名称指定认证信息，
      * ParameterClient 则指定参数名称。
      *
      * （jwt token 的生成参见 AuthController.jwtGenerate 方法）
      * */
    @Provides
    def headerClient: HeaderClient = {
        // jwtAuthenticator 接受两个参数，第一个是 SignatureConfiguration，第二个是可选的 EncryptionConfiguration，详见：
        //   http://www.pac4j.org/docs/authenticators/jwt.html
        val jwtAuthenticator = new JwtAuthenticator(new SecretSignatureConfiguration(appConf.get[String]("pac4j.security.jwt_secret")))

        // HeaderClient 的实现：
        /*
         * 从 jwt token URL 获得 jwt token 后，将它存放在 HTTP Header 中提交到该 URL:
         *
         * Http header:
         *   Authorization: Barear eyJhbGciOiJIUzI1NiJ9....
         */
        new HeaderClient("Authorization", "Bearer ", jwtAuthenticator)
    }

    /**
     * ParameterClient 的实现(假设参数名称是 "token" )：
     * */
    @Provides
    def parameterClient: ParameterClient = {
        val jwtAuthenticator = new JwtAuthenticator()
        jwtAuthenticator.addSignatureConfiguration(new SecretSignatureConfiguration(appConf.get[String]("pac4j.security.jwt_secret")))
        val parameterClient = new ParameterClient("token", jwtAuthenticator)
        parameterClient.setSupportGetRequest(true)
        parameterClient.setSupportPostRequest(false)
        parameterClient
    }

    /*@Provides
    def twitterClient: TwitterClient = new TwitterClient("HVSQGAw2XmiwcKOTvZFbQ", "FSiO9G9VRR4KCuksky0kgGuo8gAVndYymr4Nl7qc8AA")*/

    /*@Provides
    def provideFacebookClient: FacebookClient = {
        val fbId = configuration.getOptional[String]("fbId").get
        val fbSecret = configuration.getOptional[String]("fbSecret").get
        new FacebookClient(fbId, fbSecret)
    }*/

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
}