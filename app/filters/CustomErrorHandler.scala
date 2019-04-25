package filters

import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future
import play.api.mvc.Results.{Status, InternalServerError}

class CustomErrorHandler extends HttpErrorHandler{
    override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = Future.successful(
        Status(statusCode)("A client error occurred: " + message)
    )

    override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = Future.successful {
        InternalServerError(views.html.error500())
    }
}
