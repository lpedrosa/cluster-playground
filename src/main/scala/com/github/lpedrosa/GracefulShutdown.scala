package com.github.lpedrosa

import akka.actor._


object Guardian {

  import Conversation._

  @SerialVersionUID(1L)
  case class Create(id: String) extends Serializable

  @SerialVersionUID(1L)
  case object NotInitialized extends Serializable

}


class Guardian extends Actor with ActorLogging {

  import Conversation._
  import Guardian._

  def receive = {
    case Create(id) =>
      val conversation = context.actorOf(Conversation.props(id))
      context.become(started(conversation))

    case _ => sender ! NotInitialized
  }

  def started(conversation: ActorRef): Receive = {
    case m: ConversationMessage => conversation.forward(m)
  }

}

object Conversation {

  def props(id: String): Props = Props(new Conversation(id))

  sealed trait ConversationMessage extends Serializable

  @SerialVersionUID(1L)
  case class AddMember(memberId: String) extends ConversationMessage
  @SerialVersionUID(1L)
  case class RemoveMember(memberId: String) extends ConversationMessage

}

class Conversation(id: String) extends Actor with ActorLogging {

  import scala.collection.mutable.Set
  import Conversation._

  val members = Set.empty[String]

  def receive = {
    case AddMember(memberId) =>
      log.info("Adding member with id: {}", memberId)
      members += memberId
    
    case RemoveMember(memberId) =>
      log.info("Removing member with id: {}", memberId)
      members -= memberId
  }

}

object GracefulShutdown extends App {

  import akka.util.Timeout

  import scala.concurrent.Await
  import scala.concurrent.duration._


  // default timeout
  val timeout = Timeout(2.seconds)
  
  val system = ActorSystem("graceful")

  // do some stuff

  val hasTerminated = system.terminate()
  Await.result(hasTerminated, timeout.duration)

}
