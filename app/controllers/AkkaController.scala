package controllers

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._


/** 1) Play 集成了 Akka, 因此可以直接注入使用 */
@Singleton
class AkkaController @Inject()(cc: ControllerComponents, actorSystem: ActorSystem)
                              (implicit ec: ExecutionContext) // 无论是缺省的还是定制的都能被隐式捕获
  extends AbstractController(cc) {

  /** 2 - Akka async) 使用 akka 实现异步 */
  def akkaAsync() = Action.async {
    getAkkaFutureMessage(1.second).map { msg => Ok(msg) }
  }

  /** 3) 通过 akka actionSystem 获得未来任务 */
  private def getAkkaFutureMessage(delayTime: FiniteDuration): Future[String] = {
    val promise: Promise[String] = Promise[String]()

    // 通过 ActionSystem 分发
    actorSystem.scheduler.scheduleOnce(delayTime) {
      // 定义 promise (Runnable)
      promise.success {
        "Hi! Akka!!"
      }
    }(actorSystem.dispatcher) // run scheduled tasks using the actor system's dispatcher

    // 触发 promise，返回 Future
    promise.future
  }
}
