package com.github.lpedrosa

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, ActorLogging, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.util.Timeout

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

object GracefulShutdown extends App {

  import Conversation._
  import Guardian._

  val system = ActorSystem("graceful")
  
  val entityExtractor: ShardRegion.ExtractEntityId = {
    case ConversationEnvelope(id, payload) => (id.toString, payload)
    case m @ Create(id) => (id.toString, m)
  }

  private def hashToShard(hash: String, numberOfShards: Int) = {
    (hash.codePointAt(hash.length() - 1) % numberOfShards).toString
  }

  val numberOfShards = 4

  val shardExtractor: ShardRegion.ExtractShardId = {
    case ConversationEnvelope(id, payload) => hashToShard(id, numberOfShards)
    case Create(id) => hashToShard(id, numberOfShards)
  }

  val region: ActorRef = ClusterSharding(system).start(
    typeName = "Conversation",
    entityProps = Props[Guardian],
    settings = ClusterShardingSettings(system),
    extractEntityId = entityExtractor,
    extractShardId = shardExtractor)

  // do some stuff

  // default timeout
  val timeout = Timeout(2.seconds)
  val hasTerminated = system.terminate()
  Await.result(hasTerminated, timeout.duration)

}
