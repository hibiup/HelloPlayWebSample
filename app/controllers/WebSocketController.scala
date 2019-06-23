package controllers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.SecurityComponents
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AbstractController, ControllerComponents, RequestHeader, WebSocket}
import play.api.libs.json._
import play.mvc.Security

import scala.concurrent.Future

@Singleton
class WebSocketController @Inject()(cc:ControllerComponents) (implicit system: ActorSystem, mat: Materializer)
        extends AbstractController(cc)
/*class WebSocketController @Inject()(val controllerComponents: SecurityComponents,
                                    val appConf: Configuration)
                                   (implicit system: ActorSystem, mat: Materializer) extends Security[CommonProfile]*/
{
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
    def ws: WebSocket = WebSocket.acceptOrResult[JsValue, JsValue] { headers: RequestHeader => {
        /**
          * 1-1) 可以根据请求的 header 决定是否拒绝接受请求. 这里我们用 acceptOrResult，所以要返回 Future[Either]
          * */
        Future.successful(//headers.session.get("Authorization") match {
            //case None =>
            //    Left(Forbidden)
            //case Some(jwt: String) =>
                /**
                  * 2) 每个客户连接(client)在 Play 中被保存为一个 ActorRef. 通过 ActorFlow.actorRef 获得它的处理函数.
                  *
                  * ActorFlow 是一个标准的 Akka Stream Flow，
                  * */
                Right(ActorFlow.actorRef { client: ActorRef =>
                    /**
                      * 3) 并将客户 ActorRef 作为参数传给自定义的 Service Actor (4). Service actor 只是简单地提供一个 echo 服务。
                      * */
                    ServiceActor.props(client)
                })
    /*}*/) } }
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
            client ! Json.parse(s"""{ "message" : "Server => $m" }|""".stripMargin)

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

    /** */
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        logger.error(reason.getMessage, reason)
        super.preRestart(reason, message)
    }

    override def postRestart(reason: Throwable): Unit = {
        logger.error(reason.getMessage, reason)
        super.postRestart(reason)
    }

    override def aroundPreRestart(reason: Throwable, message: Option[Any]): Unit = {
        logger.error(reason.getMessage, reason)
        super.aroundPreRestart(reason, message)
    }

    override def aroundPostRestart(reason: Throwable): Unit = {
        logger.error(reason.getMessage, reason)
        super.aroundPostRestart(reason)
    }
}
