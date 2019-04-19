# pac4j 的主要概念：

1) client：认证机制，用户总是会通过某个机制登录系统。client 分为两类：间接客户（indirect client）用于 UI 身份验证，直接客户（direct client）用于Web服务身份验证。

2) Authenticator 用于为 http client 提供验证.它是 ProfileService 的子组件, ProfileService 不仅验证用户,还提供创建,删除和更新操作.

3) Authorizer：授权旨在为通过身份验证的用户配置文件在当前 Web 上下文中分配权限。

4) Matcher 用于将访问规则匹配到相应的 security filter

5) Config 根据 client, authorizer 和 matchers 定义出 configuration

6) Profile 是一个经过认证的用户.它包含 id,属性,角色和权限.

7) Web 上下文是特定于 pac4j 实现的 HTTP 请求和响应的抽象，它通过一个 SessionStore 管理会话

8) Security filter 安全过滤器是对 URL 实施用户检查的地方

9) callback endpoint 完成间接客户(indirect client)的登录过程

10) logout endpoint 不解释


# 添加支持

https://github.com/pac4j/play-pac4j/wiki/Dependencies

例子：https://github.com/pac4j/play-pac4j-scala-demo/blob/master/build.sbt

# 实现

1）基于 Guice 的 AbstractModule 新建 SecurityModule

2) 新建 CustomAuthorizer 和 CustomizedHttpActionAdapter

3) 新建受保护的 Controller, 比如 AuthController. 定义它的 Security
