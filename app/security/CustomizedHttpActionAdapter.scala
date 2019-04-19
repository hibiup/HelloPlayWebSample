package security

import org.pac4j.core.context.HttpConstants
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import play.mvc.{Result, Results}

/**
  * PlayHttpActionAdapter 用于处理特定的 HTTP 错误，例如：redirections, forbidden，unauthorized．
  * 缺省的是 PlayHttpActionAdapter
  * */
class CustomizedHttpActionAdapter extends PlayHttpActionAdapter {
    override def adapt(code: Int, context: PlayWebContext): Result = {
        if (code == HttpConstants.UNAUTHORIZED) {
            Results.unauthorized(views.html.error401.render().toString()).as(HttpConstants.HTML_CONTENT_TYPE)
        } else if (code == HttpConstants.FORBIDDEN) {
            Results.forbidden(views.html.error403.render().toString()).as(HttpConstants.HTML_CONTENT_TYPE)
        } else {
            super.adapt(code, context)
        }
    }
}
