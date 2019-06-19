package controllers

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.slf4j.LoggerFactory
import play.api.test.Helpers.GET
import play.api.test.{FakeRequest, Injecting}
import akka.stream.Materializer

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class AuthControllerSpec  extends PlaySpec with GuiceOneAppPerTest with Injecting {
    val logger = LoggerFactory.getLogger(this.getClass)

    "AuthController" should {
        "GET jwt token" in {
            //val controller = new AuthController(stubControllerComponents(), app.configuration)
            val controller = inject[AuthController]
            val jwt = controller.jwtGenerate()
                    .apply(FakeRequest(GET, "/jwt")
                            .withHeaders("Authorization" -> "Basic YWRtaW46YWRtaW4="))  // admin:admin

            implicit val materializer: Materializer = ???

            jwt.andThen{
                case Success(result) => {
                    assert(result.header.status === 200)
                    result.body.consumeData{
                        case Success(body) =>
                            println(body)
                    }
                }
            }

            Await.result(jwt, Duration.Inf)
        }
    }
}
