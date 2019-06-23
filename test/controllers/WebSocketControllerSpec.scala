package controllers

import java.util.function.Consumer

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import com.google.inject.Inject
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class WebSocketControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
    val logger = LoggerFactory.getLogger(this.getClass)

    implicit val sys = ActorSystem("MyTest")
    implicit val materializer: Materializer = ActorMaterializer()

    "Secured WebSocket" should {
        "long connection" in {
            val jwt_request = FakeRequest(GET, "/jwt").withHeaders("Authorization" -> "Basic YWRtaW46YWRtaW4=")
            val jwt_token = route(app, jwt_request).get
            status(jwt_token) mustBe OK
            contentType(jwt_token) mustBe Some("text/html")
            val token = contentAsString(jwt_token).trim
            println(token)

            /*
             * 测试用 header 传递 token
             */
            val websocketclient = new WebSocketClient()
            /**
              * 根据 WebSocketClient.connect 的定义，第二个参数会得到 Server Actor 的 Ref，用它来构造 Client Actor
              * */
            val done = websocketclient.connect("ws://localhost:9000/echo", { serverRef: ActorRef =>
                ClientActor.props(serverRef)
            })

            println(Await.result(done, Duration.Inf))

            println("End test")
        }
    }
}

/**
  * WebSocket 客户端
  * */
class WebSocketClient @Inject()()(implicit sys: ActorSystem, mat: Materializer) {
    /**
      * Creates WebSocket connection and maps it's flow to actor that will be created using given props
      *
      * @param url ws server url
      *
      * 第二个参数是一个工厂函数，传入 serverRef: ActorRef 返回 clientProp: ActorRef => Props
      */
    def connect(url: String, clientProp: ActorRef => Props, bufferSize: Int = 16, overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew): Future[Done.type] = {
        val flow = ActorFlow.actorRef(clientProp, bufferSize, overflowStrategy)
        val (upgradeResponse, _) = Http().singleWebSocketRequest(WebSocketRequest(url), flow)

        upgradeResponse.map { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
                Done
            } else {
                throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
            }
        }
    }
}


/**
  * 处理消息的客户端 Actor
  */
object ClientActor {
    def props(server: ActorRef) = Props(new ClientActor(server))
}

class ClientActor(serverRef: ActorRef) extends Actor {
    val logger = Logging(context.system, this)

    def receive = {
        case msg: JsValue => {
            /**
              * 将来自服务端的消息返回给服务端
              * */
            logger.debug(s"Receive message: $msg")   // JsValue.\\(key) 返回键值（JsValue）
            val m = (msg \ "message").as[String]
            serverRef ! Json.parse(s"""
                                   |{ "message" : "Client => $m" }
                                   |""".stripMargin)
        }
        case _ =>
            logger.info("Unknown message received")
    }

    override def preStart() = {
        serverRef ! """{"message":"Hello!"}"""
    }

    /** 当客户端关闭连接的时候.服务 Actor 的 postStop 会被调用到. */
    override def postStop() = {
        logger.info("Socket is closed") // someResource.close()
    }
}