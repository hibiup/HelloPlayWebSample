package filters

import javax.inject.Inject
import org.pac4j.play.filters.SecurityFilter
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter

/**
  * 安全配置二：
  *
  * HttpFilters 是 play 的过滤器，它用于过滤所有的请求（https://www.playframework.com/documentation/2.7.x/ScalaHttpFilters）。
  * 通过在 application.conf 中注册让 Play framework 在启动的时候自动载入：
  *
  *   play.http.filters = "filters.CustomHttpFilters"
  *
  *
  * 以上配置向 play 注册了是我们定制的 CustomHttpFilters，它加载由 pac4j 定制的 SecurityFilter. SecurityFilter 读取
  * application.conf 中的配置规则来实现对 URL 访问的过滤。：
  *
  *    pac4j.security.rules = ...
  *
  * 参见 application.conf 中的详细配置
  *
  * （除此之外，还可以通过 Secure 来直接控制 Controller 的安全配置，参见 AuthController 的说明）
  * */
class CustomHttpFilters @Inject()(securityFilter: SecurityFilter) extends HttpFilters{
    override def filters: Seq[EssentialFilter] = Seq(securityFilter)
}

/*
import play.api.libs.ws.{StandaloneWSRequest, WSRequestExecutor, WSRequestFilter}

class AuthenticatingFilter extends WSRequestFilter {
    def apply(executor: WSRequestExecutor): WSRequestExecutor = {
        req: StandaloneWSRequest =>
            ???
    }
}*/
