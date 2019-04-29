package controllers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import javax.inject.Inject
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AbstractController, ControllerComponents, WebSocket}

class WebSocketController @Inject()(cc:ControllerComponents) (implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
    def websocket = WebSocket.accept[String, String] { request =>
        ActorFlow.actorRef { out =>
            MyWebSocketActor.props(out)
        }
    }
}

object MyWebSocketActor {
    def props(out: ActorRef) = Props(new MyWebSocketActor(out))
}

class MyWebSocketActor(out: ActorRef) extends Actor {
    def receive = {
        case msg: String =>
            out ! ("I received your message: " + msg)
    }

    override def postStop() = {
        println("Socket is closed") // someResource.close()
    }
}
