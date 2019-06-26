package controllers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator
import org.pac4j.play.filters.SecurityFilter
import org.pac4j.play.scala.{Security, SecurityComponents}
import org.slf4j.LoggerFactory
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import play.api.libs.json._
import play.api.mvc.WebSocket.MessageFlowTransformer

import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.http.HttpRequestHandler
import play.api.routing.Router
import play.http.HandlerForRequest

@Singleton
class WebSocketController @Inject()(cc:ControllerComponents,
                                    val appConf: Configuration)
                                   (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
        extends AbstractController(cc)
/*class WebSocketController @Inject()(val controllerComponents: SecurityComponents,
                                    val appConf: Configuration)
                                   (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends Security[CommonProfile]*/
{
    val jwtAuthenticator = new JwtAuthenticator(new SecretSignatureConfiguration(appConf.get[String]("pac4j.security.jwt_secret")))

    /**
      * 1) WebSocket 是 Play 的 WebSocket 服务管理器，管理所有的客户端连接．通过 accept 接受一个请求, accept 的类型参数是
      * 输入和输出数据类型。传入的参数是 header 的处理函数．
      *
      * 如果使用 WebSocket.acceptOrResult 接受请求，则要求返回值必须是 Future[Either[Status, Out] 类型.
      * 如果是 WebSocket.accept 接受请求, 则返回值类型是 Future[Out]
      * */

    /**
      * pac4j 认证失效问题：
      *
      * 因为得到的是 WebSocket 而不是 Action，因此 Play 的 Filter 不能生效导致 pac4j 失效。如需实现认证需要获得 headers 后自己实现认证。
      * 或者参考：https://stackoverflow.com/questions/38208251/how-do-i-add-filters-for-websocket-requests-in-play-framework
      * */
    def ws: WebSocket = WebSocket.acceptOrResult[JsValue, JsValue] { request: RequestHeader => {
        /**
          * 1-1) 可以根据请求的 header 决定是否拒绝接受请求. 这里我们用 acceptOrResult，所以要返回 Future[Either]
          * */
        Future.successful(request.headers.get("Authorization") match {
            case None => Left(Forbidden)
            case Some(jwt: String) =>
                /**
                  * pac4j 认证失效问题：
                  *
                  * 为了弥补 pac4j 不能对 WebSocket 认证，这里需要手工加入认证. 如果token 无效，会返回 null，所以需要用到 Option
                  * */
                Option(jwtAuthenticator.validateToken(jwt.split(" ").last)) match {
                    case None => Left(Forbidden)
                    case Some(p) =>
                        /**
                          * 用户名无效或已经过期
                          * */
                        if (p.isExpired || p.getUsername.isEmpty) Left(Forbidden)
                        /**
                          * 2) 每个客户连接(client)在 Play 中被保存为一个 ActorRef. 通过 ActorFlow.actorRef 获得它的处理函数.
                          *
                          * ActorFlow 是一个标准的 Akka Stream Flow，
                          * */
                        else Right(ActorFlow.actorRef { client: ActorRef =>
                                /**
                                  * 3) 并将客户 ActorRef 作为参数传给自定义的 Service Actor (4). Service actor 只是简单地提供一个 echo 服务。
                                  * */
                                ServiceActor.props(client)
                            })
                }
    }) } }
}

/**
  * 4) 处理消息的 Server 端 Actor
  * */
object ServiceActor {
    def props(client: ActorRef) = Props(new ServiceActor(client))
}

class ServiceActor(client: ActorRef) extends Actor {
    import akka.actor.PoisonPill

    val logger = LoggerFactory.getLogger(this.getClass) //Logging(context.system, this)

    def receive = {
        /**
          * 4-1) 服务Actor将处理后的消息返回给客户端 ActorRef 就完成了双方一个回合的通讯.
          * */
        case msg: JsValue => {
            logger.debug(s"Receive message: $msg")   // JsValue.\\(key) 返回键值（JsValue）
            val m = (msg \ "message").as[String]
            client ! Json.parse(s"""{ "message" : "Server => $m" }""".stripMargin)

            /**
              * 5) (可选) 如果需要, 服务端可以主动关闭连接.
              * */
            //self ! PoisonPill
        }
        case _ =>
            logger.info("Unknown message received")
    }

    /** 当客户端关闭连接的时候.服务 Actor 的 postStop 会被调用到. */
    override def postStop() = {
        logger.info("Socket is closed") // someResource.close()
    }
}
