package controllers

// 参考：http://www.voidcn.com/article/p-mmsxwcuu-bqy.html

import javax.inject.Inject
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

/** 1）在 application.conf 中配置了一个 Akka 线程池，它会被当作缺省的线程池引入。
  *
  *   1-1）或通过明确声明一个定制类来获得某个确定的线程池（ my-context 线程池必须定义在 application.conf 中）：
  *   class MyExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "my-context")
  *
  * */
class AsyncController @Inject()(implicit ec: ExecutionContext, // 无论是缺省的还是定制的都能被隐式捕获
                                cc: ControllerComponents) extends AbstractController(cc) {

  /** 2）定义异步任务函数，这个函数返回一个 Future，并被 controller 的入口方法调用。 */
  def futureJob()= {
    Future {
      "Yes"
    }
  }

  /** 3）使用 Action.async 方法，该方法接受一个 Future 参数来异步执行任务 */
  def async() = Action.async {
    val asyncApiResponse: Future[String] = futureJob()

    /** 4）将异步任务的结果映射成 Response */
    asyncApiResponse.map(value => Ok("Api Result: " + value))
  }
}
