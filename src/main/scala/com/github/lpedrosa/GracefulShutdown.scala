package com.github.lpedrosa

import akka.actor._


object GuardianAction {

  @SerialVersionUID(1L)
  case class Create(id: String) extends Serializable
  @SerialVersionUID(1L)
  case class ConversationEnvelope(id: String, msg: ConversationMessage) 
    extends Serializable
}


trait ConversationMessage extends Serializable

object ConversationAction {

  @SerialVersionUID(1L)
  case class AddMember(memberId: String) extends ConversationMessage
  @SerialVersionUID(1L)
  case class RemoveMember(memberId: String) extends ConversationMessage

}


object Conversation {

  def props(id: String): Props = Props(new Conversation(id))

}

class Conversation(id: String) extends Actor with ActorLogging {

  import scala.collection.mutable

  import ConversationAction._

  val members = mutable.Set.empty[String]

  def receive = {
    case AddMember(memberId) => { 
      log.info("Adding member with id: {}", memberId)
      members += memberId
    }
    case RemoveMember(memberId) => {
      log.info("Removing member with id: {}", memberId)
      members -= memberId
    }
  }

}

object GracefulShutdown extends App {

  import akka.util.Timeout

  import scala.concurrent.Await
  import scala.concurrent.duration._


  // default timeout
  val timeout = Timeout(2 seconds)
  
  val system = ActorSystem("graceful")

  // do some stuff

  val hasTerminated = system.terminate()
  Await.result(hasTerminated, timeout.duration)

}
