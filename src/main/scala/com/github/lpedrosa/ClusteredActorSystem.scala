package com.github.lpedrosa

import scala.collection.JavaConverters._ 
import scala.collection.immutable.{List, Map, Set}

import akka.actor.{ActorRef, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

case class ClusterConfig(systemName: String, host: String, port: Int) {

  lazy val toConfig: Config = {
    val settings = Map(
      "akka.actor.provider" -> "cluster",
      "akka.remote.log-lifecycle-events" -> "off",
      "akka.remote.netty.tcp.hostname" -> host,
      "akka.remote.netty.tcp.port" -> port,
      "akka.cluster.sharding.journal-plugin-id" -> "akka.persistence.journal.inmem")

    val clusterMap = ConfigFactory.parseMap(settings.asJava)
    val defaultConfig = ConfigFactory.load()

    clusterMap.withFallback(defaultConfig)
  }
}

object ClusteredActorSystem {

  def apply(systemName: String, host: String = "localhost", port: Int = 5000) = {
    val config = ClusterConfig(systemName, host, port)
    val system = ActorSystem(systemName, config.toConfig)
    new ClusteredActorSystem(system, config)
  }

}

class ClusteredActorSystem(val system: ActorSystem, config: ClusterConfig) {

  private val log = LoggerFactory.getLogger(classOf[ClusteredActorSystem])
  private var cluster: Option[Cluster] = None

  def join(systemName: String = config.systemName, 
           host: String = config.host, 
           port: Int = config.port): ActorSystem = {
    cluster = Some(Cluster(system))
      
    cluster.foreach(_.join(Address("akka.tcp", systemName, host, port)))

    system
  }

  def leave(): ActorSystem = {
    cluster match {
      case Some(cluster) => cluster.leave(cluster.selfAddress)
      case None => log.warn("Tried to leave a cluster without joining one first...")
    }
    system
  }

}

object SharderSetup {

  import Conversation._
  import Guardian._

  private val entityExtractor: ShardRegion.ExtractEntityId = {
    case ConversationEnvelope(id, payload) => (id.toString, payload)
    case m @ Create(id) => (id.toString, m)
  }

  private def hashToShard(hash: String, numberOfShards: Int) = {
    (hash.codePointAt(hash.length() - 1) % numberOfShards).toString
  }

  private val numberOfShards = 4

  private val shardExtractor: ShardRegion.ExtractShardId = {
    case ConversationEnvelope(id, payload) => hashToShard(id, numberOfShards)
    case Create(id) => hashToShard(id, numberOfShards)
  }

  def apply(system: ActorSystem): ActorRef = {
    ClusterSharding(system).start(
      typeName = "Conversation",
      entityProps = Props[Guardian],
      settings = ClusterShardingSettings(system),
      extractEntityId = entityExtractor,
      extractShardId = shardExtractor)
  }

}
