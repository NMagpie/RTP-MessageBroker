package topic

import akka.actor.ActorRef
import akka.persistence.{PersistentActor, RecoveryCompleted}
import network.MessagesHandler.Message
import topic.Topic.{Subscribe, Unsubscribe}

/*

  Topic Actor - receives messages of corresponding topic and forwards it to all the consumers which are subscribed to it.
  Persist list of subscribers in case of exception, failure and restart.

 */

object Topic {
  case class Subscribe(subscriber: ActorRef)

  case class Unsubscribe(subscriber: ActorRef)
}

class Topic(name: String) extends PersistentActor {

  override def persistenceId: String = s"Topic-$name"

  //val router: Router = Router(BroadcastRoutingLogic(), Vector[ActorRefRoutee]())

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println(s"Topic[$name] is restarting!")
  }

  var router: Set[ActorRef] = Set.empty[ActorRef]

  override def preStart(): Unit = super.preStart()

  // Leftovers of the non-persistent mailbox

  //  override def receive: Receive = {
  //    case a @ Message(_, message, _) =>
  //      router.foreach(consumer => consumer ! a)
  //      //router.route(a, self)
  //      //println(s"Topic $name: \'${message}\'")
  //
  //    case Subscribe(subscriber) =>
  //      router += subscriber
  //      //router.addRoutee(subscriber)
  //      //subscriber ! PreviousMessages(buffer)
  //
  //    case Unsubscribe(subscriber) =>
  //      router -= subscriber
  //      //router.removeRoutee(subscriber)
  //
  //    case _ =>
  //  }

  val receiveRecover: Receive = {
    case Subscribe(subscriber) =>
      router += subscriber

    case Unsubscribe(subscriber) =>
      router -= subscriber

    case RecoveryCompleted =>
  }

  val receiveCommand: Receive = {
    case a@Message(_, _, _) =>
      router.foreach(consumer => consumer ! a)

    case s@Subscribe(subscriber) =>
      persist(s) {
        _ =>
          router += subscriber
      }
    case s@Unsubscribe(subscriber) =>
      persist(s) {
        _ =>
          router -= subscriber
      }

    case _ =>
  }
}
