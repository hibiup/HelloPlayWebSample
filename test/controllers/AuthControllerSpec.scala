package controllers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.slf4j.LoggerFactory
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.Helpers.{GET, contentAsString, contentType, route, status}
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

            /**
              * 注意：虽然本例中使用了 token 但是因为 inject 方式将直接访问 jwt 方法，因此实际上绕过了认证机制，无需 token
              * 也可以得到结果, 因此并不能证明认证机制的有效性。
              * */
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

        "GET by jwt token" in {
            import play.api.test._
            import play.api.test.Helpers._

            /**
              * 只有通过 route 机制来访问服务才连同验证机制一起得到检验。
              * */
            val jwt_request = FakeRequest(GET, "/jwt").withHeaders("Authorization" -> "Basic YWRtaW46YWRtaW4=")
            val jwt_token = route(app, jwt_request).get
            status(jwt_token) mustBe OK
            contentType(jwt_token) mustBe Some("text/html")
            val token = contentAsString(jwt_token).trim
            println(token)

            /*
             TODO: 会遇到 No client found for name: ParameterClient 错误，因为我们在 application.conf 中为 /jwt/ 定义了
                   ParameterClient 机制，需要在 SecurityModule 中实现并加载 ParameterClient
            */
            val request = FakeRequest(GET, "/jwt/info?").withHeaders("Authorization" -> s"Bearer $token")
            val info = route(app, request).get
            contentType(info) mustBe Some("text/html")
            val content = contentAsString(info).trim
            println(content)
        }
    }
}
