# 新建 Play 项目

执行以下指令：
```
$ sbt new playframework/play-scala-seed.g8
...下载支持...
name [play-scala-seed]: <输入项目名称>
organization [com.example]: ...

```

缺省会生成 sbt 和 gradle 混合项目，可以删除 gradle 配置，然后import 到 intellij，或 Open as Project 的时候选择 build.sbt

# 项目结构

app：应用主体，通常是后台相关的源码
 
    controllers：控制器，接口逻辑相关
    views：视图，网页内容相关
    models：模型，数据相关

conf：配置目录

    application.conf：应用配置，可以覆盖子模块中的默认配置 reference.conf
    routes：路由器，配置所有接口的地方
    logback.xml: Log 配置文件

project：工程目录

    plugins.sbt：插件配置文件，可以用来配置插件等。

public：公共资源

    images：图片
    javascripts：JS 脚本
    stylesheets：CSS 样式

test：测试目录

build.sbt：项目构建文件

# 项目启动

在项目根目录下执行：
```
$ sbt run
```
缺省服务端口是 9000

或在 idea 中新建一个 debug configuration，选取 Play 2 App模板，然后执行。

# 发布项目

首先对于即将投入生产环境的应用，play 都会要求一个 secret key，值是任意随机字符串，确认其配置在 application.conf 文件中:

~~~config
play.http.secret.key="Xoxoxoxox"
~~~

##　打包项目：

~~~sbtshell
# sbt dist
~~~

以上命令会在 target\universal 目录下生成 zip 发布包.

## 在目标机上解压发布包

解压后在 bin 目录下有两个可执行脚本，修改 `windows batch` 脚本中的 `APP_CLASSPATH` 环境变量为：

~~~bash
set "APP_CLASSPATH=%APP_LIB_DIR%\..\conf\;%APP_LIB_DIR%\*"
set "APP_MAIN_CLASS=play.core.server.ProdServerStart"
~~~

否则 `APP_CLASSPATH` 的值会因为太长而无法被载入。

## 执行

执行的时候需要指定 `-Dconfig.file` 参数

```shell
# <project-version>/bin/xxx -Dconfig.file=<project-version>/conf/application.conf
```

# 项目开发

Idea Ultimate 版本支持 Play framework，社区版不支持。

# 调试

启动项目:
~~~
$ sbt run -jvm-debug 9999
~~~

然后配置 intellij 的 remote, 只需修改端口为 9999, 然后执行即可
