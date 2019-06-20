package controllers

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import org.slf4j.LoggerFactory
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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
            val request: WSRequest = AhcWSClient().url("ws://localhost:9000/ws")
            request.addHttpHeaders("Authorization" -> s"Bearer $token").withBody("""{"message":"Hello?"}""")

            val response: Future[WSResponse] = request.get()

            response.flatMap { res =>
                res.bodyAsSource.runWith(Sink.fold[Long, ByteString](0L) { (total, bytes) =>
                    total + bytes.length
                })
            }
        }
    }
}
