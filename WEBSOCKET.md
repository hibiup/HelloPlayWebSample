启动的时候可选设置 WebSocket 缓冲区大小：

sbt -Dwebsocket.frame.maxLength=64k run

然后登录：https://www.websocket.org/echo.html 输入 ws://localhost:9000/ws 进行测试．