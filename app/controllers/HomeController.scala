package controllers

import javax.inject._
import play.api.mvc._

// 参考：https://dzone.com/refcardz/getting-started-play-framework?chapter=1

/**
  * 1) Play内部的 DI 组件都用的是 Google Guice。只要是符合JSR-330标准的DI组件都可用于Play Framework中。因此以下的 @Inject()
  *    表示 HomeController 依赖注入了 ControllerComponents 组件的实例 cc。 Guice 里面的依赖注入有好几种方式：构造注入、方法注
  *    入 等等。@Inject() 插入在类名之后，构造参数列表之前，表示构造注入。
  *
  *    Guice 的缺省组件声明周期管理模型是非单例模型，@Singleton 表示我们将采用单例模型。
  *
  *    ControllerComponents 实例提供了一些有用的函数。在这个例子中我们没有直接使用这些功能。
  */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
    * 2) 请求的转发由 conf/routes 文件定义
    *
    * 3) Controller 的基本执行单元是 Action（object），它隐性输入 Request, 返回 Response. Response 以返回码字面符为函数名。
    *
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    /**
      * 4) views: 编译器会按照路径约定，将 "views" 目录下的文件编译成 class. “views” 名称是非必须的，可以使用其它路径名称，
      *    这个名称会被编译成包名。比如使用 “ui" 作为目录名，那么生成的 class 的包名就是 ui...
      *
      *    html: 如果我们使用 html 作为界面模板，那么文件名就以 html 结尾，路径名称也由此约定生成。
      *
      *    index: 函数名称（object class）来自模板文件名。模板文件名的命名约定是 "文件名.目标语言.模板语言"，对应的引用约定是
      *    “目录名.模板语言.函数名（文件名）” 因此每个模板都被转换成一个 object。模板的第一行定义 apply 函数的参数，接下去参考
      *    index.scala.html 的说明
      * */
    Ok(views.html.index())
      .withHeaders("Foo" -> "bar")
      .withSession("SessionFoo" -> "The-value")
  }
}
