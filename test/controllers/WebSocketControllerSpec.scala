package controllers

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import com.google.inject.Inject
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import org.slf4j.LoggerFactory
import play.api.libs.streams.ActorFlow
import play.api.test._
import play.api.test.Helpers._

import scala.collection.immutable.{Seq => MSeq}
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
              * 测试方法一（推荐）
              * */
            val (wsActorRef: ActorRef, connected) = websocketclient.connect(
                "ws://localhost:9000/echo",
                RawHeader("Authorization", s"Bearer $token") :: Nil,
                { message: Message => {
                    println(s"Received text message: [$message]")
                    // TODO：如何将消息返回给 wsActorRef?
                } }
            )

            /*
            // 测试方法二（失败，详见 ClientActor.receive）
            connected = websocketclient.connect2(
                "ws://localhost:9000/echo",
                RawHeader("Authorization", s"Bearer $token") :: Nil,
                { serverRef: ActorRef => ClientActor.props(serverRef) }
            )*/

            /**
              * 等待连接建立
              * */
            Await.result(connected, Duration.Inf)

            wsActorRef ! TextMessage.Strict("""{"message":"Hello!"}""")
            //wsActorRef ! TextMessage.Strict("""{"message":"Hello again!"}""")
            //wsActorRef ! TextMessage.Strict("""{"message":"Hello again again!"}""")

            Await.result(sys.whenTerminated, Duration.Inf)
        }
    }
}

/**
  * WebSocket 客户端
  * */
class WebSocketClient @Inject()()(implicit sys: ActorSystem, mat: Materializer) {
    /**
      * 方案一（推荐）：
      *
      * 利用 webSocketClientFlow 建立 WebSocket 连接. 传入连接URI和参数，以及接受消息的回调函数。
      * 返回带有 WebSocket 的 ActorRef 和连接结束信标（Future）
      */
    def connect(uri: String,
                headers: MSeq[HttpHeader],
                messageCallback: Message => Unit,
                bufferSize: Int = 16,
                overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew): (ActorRef, Future[Done.type]) = {

        /**
          * 1) webSocketClientFlow 根据连接参数预定义出一个建立连接的呼叫流程（Flow）。注意此时还没有产生真正的连接。
          * */
        val connectionFlow = Http().webSocketClientFlow(WebSocketRequest(uri, headers))

        /**
          * 2) 消息处理Flow, 调用回调函数处理接受到的消息.
          * */
        val messageFlow = Flow[Message].map(message => messageCallback(message))

        /**
          * 3) 定义服务的终点 Sink。
          * */
        val messageSink: Sink[Message, _] = messageFlow.to(Sink.ignore)  // 结束

        /**
          * 4) 定义流程起点（Source）。Source 的第二个参数是 ActorRef（而不是通常的 NotUsed），也就意味着我们将返回
          * WebSocket ActorRef
          * */
        val messageSource: Source[Message, ActorRef] = Source.actorRef[TextMessage.Strict](bufferSize, overflowStrategy)

        /** 5) 执行整个流程，返回 WebSocket Actor, Future[WebSocketUpgradeResponse] */
        val ((wsActorRef, upgradeResponse), closed) = messageSource
            .viaMat(connectionFlow)(Keep.both)
            .toMat(messageSink)(Keep.both)
            .run()

        /**
          * 6) 执行连接，并获取 WebSocketUpgradeResponse 执行信标
          * */
        val connected = upgradeResponse.flatMap { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
                Future.successful(Done)
            } else {
                throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
            }
        }

        /**
          * 7) 返回 WebSocket ActorRef 和信标
          * */
        (wsActorRef, connected)
    }

    // 以下方案执行失败
    /**********************************************************************
     *
     * 方案二(不成功)：利用 singleWebSocketRequest 建立 WebSocket 连接。
     * */
    def connect2(uri: String,
                headers:MSeq[HttpHeader],
                clientProp: ActorRef => Props,
                bufferSize: Int = 16,
                overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew): Future[Done.type] = {

        /**
          * 1) 消息处理 Flow, 这里我们演示使用 ActorFlow 来处理消息。与 Flow 隐藏 Actor 调用细节不同。ActorFlow
          * 显式接受一个 Prop 来产生处理数据的 Actor，这让我们可以定制处理过程。
          * */
        val messageFlow = ActorFlow.actorRef(clientProp, bufferSize, overflowStrategy)

        /**
          * 2) 与 webSocketClientFlow 不同，singleWebSocketRequest 不返回连接 Flow，也不需定义 Sink，它只接受一个
          * 处理消息的 Flow，然后直接发起连接。
          * */
        val (upgradeResponse, closed) = Http().singleWebSocketRequest(
            WebSocketRequest(uri, headers),
            messageFlow
        )

        /**
          * 3) 获得并返回连接信标
          * */
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
  * 为方案二准备的处理消息的客户端 Actor。方法一不需要此 Actor
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

    def receive: Receive = {
        /**
          * 将来自服务端的消息返回给服务端
          * */
        case TextMessage.Strict(msg) => {
            logger.debug(s"Receive message: $msg")
            /**
              * ！失败，原因未知！
              *
              * 如果使用 sender() 消息会发送回 clientActor 自己。如果使用 serverRef 会导致连接中断。
              * */
            /*sender()*/ serverRef ! TextMessage(s"""{ "message" : "Client => $msg" }""".stripMargin)
        }
        /**
          * 或使用 Sink 处理 Message. 需要隐式物化器
          * */
        /*case msg: TextMessage => {
            logger.debug(s"Receive message: $msg")
            msg.textStream.runWith(Sink.foreach(m =>
                serverRef ! TextMessage(s"""{ "message" : "Client => $m" }""".stripMargin)))
        }*/
        case _ => logger.info("Unknown message received")
    }

    override def preStart() = {
        serverRef ! TextMessage(s"""{ "message" : "Hello!" }""".stripMargin)
    }

    /** 当客户端关闭连接的时候.服务 Actor 的 postStop 会被调用到. */
    override def postStop() = {
        logger.info("Socket is closed") // someResource.close()
        sys.terminate()   // 关闭 Akka system
    }
}