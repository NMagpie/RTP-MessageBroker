package listener

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.io.Tcp
import akka.stream.alpakka.sse.scaladsl.EventSource
import akka.stream.scaladsl.Source
import akka.util.ByteString
import main.Main.system
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.json4s.{Formats, NoTypeHints, jackson}
import network.TcpClient

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

object Listener {

  case class GetData(json: String)

  case class Tweet(id: Int, message: String, topic: String)

  case class Message(id: Int, message: String, topic: String)

  case class OpenSource()

  var ids: AtomicInteger = new AtomicInteger(0)

  val panicMessage = "{\"message\": panic}"

}

class Listener(val number: Int) extends Actor {

  import Listener._
  import Tcp._

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  val client: ActorRef = system.actorOf(TcpClient.props(new InetSocketAddress("localhost",8000), self))

  val send: HttpRequest => Future[HttpResponse] = Http().singleRequest(_)

  var eventSource: Option[Source[ServerSentEvent, NotUsed]] = None

  var events: Option[Future[Done]] = None

  def openSource(): Unit = {
    eventSource = Option(EventSource(
      uri = Uri(s"http://localhost:4000/tweets/${this.number}"),
      send,
    ))

    events = Option(eventSource
      .get
      .runForeach(newTweet))
  }

  def newTweet(event: ServerSentEvent): Unit = {

    if (event.data == panicMessage) {
      //println("Panic!\n")
      //self ! Kill
      return
    }

    val tweet = parse(event.data)

    val text = (tweet \ "message" \ "tweet" \ "text").extract[String]

    val topic = (tweet \ "message" \ "tweet" \ "user" \ "lang").extract[String]

    self ! Tweet(ids.getAndIncrement(), text, topic)

  }

  def receive: Receive = {

    case Connected(_, _) =>
      println("Connected to the server!")
      openSource()

    case CommandFailed(_: Connect) =>
      println("Unable to connect to the server!")
      System.exit(0)

    case a @ Tweet(_, _, _) =>
      //println(Serialization.write(a))

      val byteMessage = ByteString.fromString(Serialization.write(a))

      client ! byteMessage
  }

}
