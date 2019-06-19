package controllers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.slf4j.LoggerFactory
import play.api.libs.streams.Accumulator
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.Helpers.GET
import play.api.test.{FakeRequest, Injecting}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.Success

class AuthControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
    val logger = LoggerFactory.getLogger(this.getClass)

    implicit val sys = ActorSystem("MyTest")
    implicit val materializer: Materializer = ActorMaterializer()

    "AuthController" should {
        "GET jwt token" in {
            def request(action: Action[AnyContent], uri: String, token: String): Future[Result] = action
                .apply(FakeRequest(GET, uri)
                    .withHeaders("Authorization" -> token))

            val controller = inject[AuthController]
            def jwt = request(controller.jwtGenerate(), "jwt", "Basic YWRtaW46YWRtaW4=")  // admin:admin

            val comp = jwt.andThen{
                case Success(result) => {
                    assert(result.header.status === 200)
                    result.body.consumeData.map{s => {
                        val token = s.utf8String.trim
                        println(token)

                        request(controller.jwt_info, "/jwt/info", s"Bearer $token").andThen{
                            case Success(resp) => {
                                assert(resp.header.status === 200)
                                resp.body.consumeData.map{s1 => {
                                    val content = s1.utf8String.trim
                                    println(content)
                                }
                            }
                        } }
                    } }
                }

                case _ => fail
            }

            Await.result(comp, Duration.Inf)
        }
    }
}
