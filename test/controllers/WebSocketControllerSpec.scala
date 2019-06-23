package controllers

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import akka.stream.scaladsl.Sink
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

            Await.result(sys.whenTerminated, Duration.Inf)
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

import akka.pattern.ask

/**
  * 处理消息的客户端 Actor
  */
object ClientActor {
    def props(server: ActorRef)(implicit sys: ActorSystem, mat: Materializer) = Props(new ClientActor(server))
}

/**
  * implicit sys: ActorSystem： 参数仅仅是为了完成测试案例后关闭akka，真实应用中不需要这个参数。
  * mat: Materializer： 当用 Sink 来处理返回消息时需要用到物化器，如果不使用 Sink 来处理消息，可以不需要这个参数。
  * */
class ClientActor(serverRef: ActorRef)(implicit sys: ActorSystem, mat: Materializer) extends Actor {
    val logger = LoggerFactory.getLogger(this.getClass)

    def receive = {
        /**
          * 将来自服务端的消息返回给服务端
          * */
        case TextMessage.Strict(msg) => {
            logger.debug(s"Receive message: $msg")
            serverRef ! TextMessage(s"""{ "message" : "Client => $msg" }""".stripMargin)
        }
        /**
          * 或使用 Sink 处理 Message. 需要隐式物化器
          * */
        /*case msg: TextMessage => {
            logger.debug(s"Receive message: $msg")
            msg.textStream.runWith(Sink.foreach(m =>
                serverRef ! TextMessage(s"""{ "message" : "Client => $m" }""".stripMargin)))
        }*/
        case _ =>
            logger.info("Unknown message received")
    }

    override def preStart() = {
        serverRef ! TextMessage(s"""{ "message" : "Hello!" }""".stripMargin)
    }

    /** 当客户端关闭连接的时候.服务 Actor 的 postStop 会被调用到. */
    override def postStop() = {
        logger.info("Socket is closed") // someResource.close()
        sys.terminate()   // 关闭 Akka system
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