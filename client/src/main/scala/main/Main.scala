package main

import akka.actor.{ActorRef, ActorSystem, Props}
import client.Overlord
import client.Overlord.CreateClients

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var overlord: Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    overlord.get ! CreateClients(1)
  }

}
