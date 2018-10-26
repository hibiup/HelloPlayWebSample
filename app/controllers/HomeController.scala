package controllers

import javax.inject._
import play.api._
import play.api.mvc._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
    * 1) 请求的转发由 conf/routes 文件定义
    *
    * 2) Controller 的基本执行单元是 Action，这个函数隐性输入 Request, 返回 Response. Response 以返回码字面符为函数名。
    *
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    /**
      * 3) views: 编译器会按照路径约定，将 "views" 目录下的文件编译成 class. “views” 名称是非必须的，可以使用其它路径名称，
      *    这个名称会被编译成包名。比如使用 “ui" 作为目录名，那么生成的 class 的包名就是 ui...
      *
      *    html: 如果我们使用 html 作为界面模板，那么文件名就以 html 结尾，路径名称也由此约定生成。
      *
      *    index: 函数名称（object class）来自模板文件名。模板文件名的命名约定是 "文件名.目标语言.模板语言"，对应的引用约定是
      *    “目录名.模板语言.函数名（文件名）” 因此每个模板都被转换成一个 object。模板的第一行定义 apply 函数的参数，接下去参考
      *    index.scala.html 的说明
      * */
    Ok(views.html.index())
  }
}
