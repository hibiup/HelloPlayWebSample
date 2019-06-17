package controllers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.stream.Materializer
import javax.inject.Inject
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AbstractController, ControllerComponents, RequestHeader, WebSocket}
import play.libs.Json

import scala.concurrent.Future

class WebSocketController @Inject()(cc:ControllerComponents) (implicit system: ActorSystem, mat: Materializer)
        extends AbstractController(cc) {
    /**
      * 1) WebSocket 是 Play 的 WebSocket 服务管理器，管理所有的客户端连接．通过 accept 接受一个请求,
      * accept 的类型参数是输入和输出数据类型。传入的参数是 header 的处理函数．
      *
      * 如果使用 WebSocket.acceptOrResult 接受请求，则要求返回值必须是 Future[Either[Status, Out] 类型.
      * 如果是 WebSocket.accept 接受请求, 则返回值类型是 Future[Out]
      * */
    def websocket = WebSocket.acceptOrResult[JsValue, JsValue] { headers: RequestHeader => {
        /**
          * 1-1) 可以根据请求的 header 决定是否拒绝接受请求. 这里我们用 acceptOrResult，所以要返回 Future[Either]
          * */
        Future.successful(headers.session.get("user") match {
            case None => Left(Forbidden)
            case Some(username) =>
                /**
                  * 2) 每个客户连接(client)在 Play 中被保存为一个 ActorRef. 通过 ActorFlow.actorRef 获得它的处理函数.
                  *
                  * ActorFlow 是一个标准的 Akka Stream Flow，
                  * */
                Right(ActorFlow.actorRef { client: ActorRef =>
                    /**
                      * 3) 并将客户 ActorRef 作为参数传给服务 Actor.
                      * */
                    ServiceActor.props(client)
                })
    }) } }
}

object ServiceActor {
    def props(client: ActorRef) = Props(new ServiceActor(client))
}

class ServiceActor(client: ActorRef) extends Actor {
    import akka.actor.PoisonPill

    val logger = Logging(context.system, this)

    def receive = {
        /** 4) 服务Actor将处理后的消息返回给客户端 ActorRef 就完成了双方一个回合的通讯. */
        case msg: JsValue => {
            logger.debug(s"Receive message: $msg")   // JsValue.\\(key) 返回键值（JsValue）
            client ! Json.parse(s"""
                   |{ "message" : "I received your message: ${(msg \\ "message").toString()} }
                   |""".stripMargin)

            /** 5) (可选) 如果需要, 服务端可以主动关闭连接. */
            self ! PoisonPill
        }
    }

    /** 当客户端关闭连接的时候.服务 Actor 的 postStop 会被调用到. */
    override def postStop() = {
        logger.info("Socket is closed") // someResource.close()
    }
}
