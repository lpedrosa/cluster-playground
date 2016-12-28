package com.github.lpedrosa

import akka.actor.{Actor, ActorRef, ActorLogging, ActorSystem, Props}

object Guardian {

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
  case class ConversationEnvelope(id: String, payload: ConversationMessage) 
    extends Serializable

  @SerialVersionUID(1L)
  case class AddMember(memberId: String) extends ConversationMessage
  @SerialVersionUID(1L)
  case class RemoveMember(memberId: String) extends ConversationMessage

}

class Conversation(id: String) extends Actor with ActorLogging {

  import Conversation._

  val members = scala.collection.mutable.Set.empty[String]

  def receive = {
    case AddMember(memberId) =>
      log.info("Adding member with id: {}", memberId)
      members += memberId
    case RemoveMember(memberId) =>
      log.info("Removing member with id: {}", memberId)
      members -= memberId
  }

}

