package network

import akka.actor.{ActorRef, Cancellable}
import akka.io.Tcp
import akka.persistence.PersistentActor
import akka.util.ByteString
import main.Main.{topicPool, topicSup}
import network.MessagesHandler.{ConnectionType, GiveTopics, SubscribeToAll, TopicsSelected}
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import topic.Topic.{Subscribe, Unsubscribe}
import topic.TopicSupervisor.CreateTopic

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object MessagesHandler {
  case class Message(id: Int, message: String, topic: String)

  case class SubscribeToAll()

  case class GiveTopics()

  case class ConnectionType(connType: ByteString)

  case class TopicsSelected(topics: ByteString)

}

class MessagesHandler(connection: ActorRef) extends PersistentActor {
  import Tcp._
  import main.Main.system
  import MessagesHandler.Message

  override def persistenceId: String = self.path.name

  implicit val executor: ExecutionContextExecutor = system.dispatcher

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  def producerHandler: Receive = {
    case Received(data) =>
      val message = parse(data.utf8String).extract[Message]

      if (topicPool.contains(message.topic)) {
        topicPool(message.topic) ! message
      } else {
        topicSup.get ! CreateTopic(message)
      }

    case PeerClosed => context.stop(self)

    case ErrorClosed(_) => context.stop(self)
  }

  def consumerHandler(topics : Set[String]): Receive = {
    case a @ Message(_, _, _) =>
      connection ! Write(ByteString.fromString(Serialization.write(a)))

    case SubscribeToAll =>
      for (topic <- topics) {
        if (topicPool.contains(topic))
          topicPool(topic) ! Subscribe(self)
      }

    case Closed =>
      endConsumer(topics)

    case Aborted =>
      endConsumer(topics)

    case ConfirmedClosed =>
      endConsumer(topics)

    case PeerClosed =>
      endConsumer(topics)

    case ErrorClosed(_) =>
      endConsumer(topics)

    case Received(_) =>

    case a @ _ => println(a)
  }

  def selectingTopics: Receive = {
    case GiveTopics =>
      val jsonTopics = Serialization.write("topics"->topicPool.keySet)

      connection ! Write(ByteString.fromString(jsonTopics))

    case Received(data) =>
      persist(TopicsSelected(data)) { _ =>
        deleteMessages(1)
        selectTopics(data)
      }

    case Closed =>
      context.stop(self)

    case Aborted =>
      context.stop(self)

    case ConfirmedClosed =>
      context.stop(self)

    case PeerClosed =>
      context.stop(self)

    case ErrorClosed(_) =>
      context.stop(self)

    case _ =>
  }

  def endConsumer(topics: Set[String]): Unit = {
    for (topic <- topics) {
      topicPool(topic) ! Unsubscribe(self)
    }
    println("Connection closed, unsubscribed.")
    context.stop(self)
  }

  var timeOutTask : Option[Cancellable] = None

  def scheduleMessage(): Unit = {

  }

  def scheduleTimeout(): Unit = {
    timeOutTask = Option(system.scheduler.scheduleOnce(5 seconds) {
      connection ! ConfirmedClose
      context.stop(self)
    })
  }

  def cancelTimeout(): Unit = {
    if (timeOutTask.isDefined) {
      timeOutTask.get.cancel()
      timeOutTask = None
    }
  }

  override def receiveRecover: Receive = {

    case ConnectionType(data) =>
      connType(data)

    case TopicsSelected(data) =>
      selectTopics(data)

    case Connected(_, _) =>
      scheduleTimeout()

    case Closed =>
      context.stop(self)

    case Aborted =>
      context.stop(self)

    case ConfirmedClosed =>
      context.stop(self)

    case PeerClosed =>
      context.stop(self)

    case ErrorClosed(_) =>
      context.stop(self)
  }

  override def receiveCommand: Receive = {

    case Received(data) =>
      persist(ConnectionType(data)) ( _ => connType(data))

    case Connected(_, _) =>
      scheduleTimeout()

    case Closed =>
      context.stop(self)

    case Aborted =>
      context.stop(self)

    case ConfirmedClosed =>
      context.stop(self)

    case PeerClosed =>
      context.stop(self)

    case ErrorClosed(_) =>
      context.stop(self)
  }

  def selectTopics(data: ByteString): Unit = {
    val jsonTopics = parse(data.utf8String)
    val topics = (jsonTopics \ "topics").extract[Set[String]]

    cancelTimeout()

    self ! SubscribeToAll

    context.become(consumerHandler(topics))
  }

  def connType(data: ByteString): Unit = {
      val connType = parse(data.utf8String)

      (connType \ "connectionType").extract[String] match {
        case "Producer" =>
          cancelTimeout()

          context.become(producerHandler)

        case "Consumer" =>
          cancelTimeout()

          scheduleTimeout()

          self ! GiveTopics

          context.become(selectingTopics)

        case _ =>
      }
  }

}
