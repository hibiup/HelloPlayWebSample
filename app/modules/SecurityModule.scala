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
import security.{CustomAuthorizer, CustomizedHttpActionAdapter}

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
          * 随机字符串,用于加密 cookie
          * */
        val sKey = "rpkTGtoJvLIdsrPd"    // 取 16 位长
        val dataEncrypter = new ShiroAesDataEncrypter(sKey)
        val playSessionStore = new PlayCookieSessionStore(dataEncrypter)

        bind(classOf[PlaySessionStore]).toInstance(playSessionStore)                // 配置存储 Session 的存储器
        bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])    // 配置缺省的 SecurityComponents 实例,会被传给 controller
        bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])                      // 注册登陆用户的 Profile 格式, 这个 profile 会被传递给 ProfileAuthorizer

        // callback
        /**
          * 为 indirect client 配置回调页面，要在 route 中注册缺省的 CallbackController
          *
          * GET         /callback                                @org.pac4j.play.CallbackController.callback()
          * POST        /callback                                @org.pac4j.play.CallbackController.callback()
          * */
        val callbackController = new CallbackController()
        callbackController.setDefaultUrl("/?defaulturlafterlogout")     // indirect client 登出后的缺省 URL
        callbackController.setMultiProfile(true)
        bind(classOf[CallbackController]).toInstance(callbackController)

        // logout
        /**
          * 为 direct client 配置 logout 服务, 要在 route 中注册:
          *
          * GET         /logout                                  @org.pac4j.play.LogoutController.logout()
          * */
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
          * 2-1) 设置支持的认证机制(Client)，支持的认证机制包括：
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
          * 2-2) 授权(Authorizer )配置实例
          * */
        val config = new Config(clients)

        /** 为配置实例添加授权器 */
        config.addAuthorizer("admin", new RequireAnyRoleAuthorizer[Nothing]("ROLE_ADMIN"))    //  对 admin 用户授予 ROLE_ADMIN
        config.addAuthorizer("custom", new CustomAuthorizer)

        /**
          * 2-3) 添加 URL 映射
          * */
        config.addMatcher("excludedPath", new PathMatcher().excludeRegex("^/facebook/notprotected\\.html$"))   // 将该URL排除在外

        /**
          * 2-4 Option)设置 HTTP 错误的返回页, 比如 redirections, forbidden, unauthorized 等。
          * */
        config.setHttpActionAdapter(new CustomizedHttpActionAdapter())

        /** 2-5) 返回配置实例 */
        config
    }

    /**
      * 认证（Authentication）
      * */
    @Provides
    def provideTwitterClient: TwitterClient = new TwitterClient("HVSQGAw2XmiwcKOTvZFbQ", "FSiO9G9VRR4KCuksky0kgGuo8gAVndYymr4Nl7qc8AA")

    @Provides
    def provideDirectBasicAuthClient: DirectBasicAuthClient = new DirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator)

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