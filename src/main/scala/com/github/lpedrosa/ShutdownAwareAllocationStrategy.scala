package com.github.lpedrosa

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardCoordinator.LeastShardAllocationStrategy
import akka.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import akka.cluster.sharding.ShardRegion.ShardId
import org.slf4j.LoggerFactory


/**
 * Provides a way to manage the blacklisted nodes.
 *
 * e.g. These nodes would be updated during a graceful shutdown
 */
trait BlacklistService {

  def add(hostPort: String): Unit
  def remove(hostPort: String): Unit
  def listAll(): Future[immutable.Set[String]]

}

class InMemBlacklistService extends BlacklistService {

  private val blacklist: scala.collection.mutable.Set[String] = scala.collection.mutable.Set.empty[String]

  override def add(hostPort: String): Unit = {
    blacklist += hostPort
  }

  override def remove(hostPort: String): Unit = {
    blacklist -= hostPort
  }

  override def listAll(): Future[immutable.Set[String]] = {
    Future.successful(blacklist.toSet)
  }

}

object ShutdownAwareAllocationStrategy {

  def apply(system: ActorSystem, blacklistService: BlacklistService, ec: ExecutionContext): ShutdownAwareAllocationStrategy = {
    val settings = ClusterShardingSettings(system)

    // akka uses the LeastShardAllocationStrategy by default, we just want to decorate it
    val delegate = new LeastShardAllocationStrategy(
      settings.tuningParameters.leastShardAllocationRebalanceThreshold,
      settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance)

    val selfAddress = Cluster(system).selfAddress.hostPort

    new ShutdownAwareAllocationStrategy(selfAddress, delegate, blacklistService, ec)
  }

}

class ShutdownAwareAllocationStrategy(selfAddress: String,
                                      delegate: ShardAllocationStrategy, 
                                      blacklistService: BlacklistService,
                                      ec: ExecutionContext) extends ShardAllocationStrategy {

    private val log = LoggerFactory.getLogger(classOf[ShutdownAwareAllocationStrategy])

    override def allocateShard(
      requester: ActorRef, 
      shardId: ShardId, 
      currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {

      log.info("currentShardAllocations: {}", currentShardAllocations)
      // filter out shards marked for shutdown
      val liveShardAllocations = filterShards(currentShardAllocations)
      liveShardAllocations.flatMap { liveShards => 
        log.info("liveShards(filtered): {}", liveShards)
        delegate.allocateShard(requester, shardId, liveShards)
      }(ec)
    }

    override def rebalance(
      currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
      rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {
        // do nothing special here
        // TODO(lpedrosa): should take into account shards that are shutting down, here as well
        delegate.rebalance(currentShardAllocations, rebalanceInProgress)
    }

    private def filterShards(currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]) = {
      val filteredShards = blacklistService.listAll().map { blacklist =>
        currentShardAllocations.filter { case (k,v) =>
          !blacklist.contains(toHostPort(k))
        }
      }(ec)
      filteredShards
    }

    private def toHostPort(ref: ActorRef): String = {
      val address = ref.path.address

      val hostPort = address.port match {
        case Some(port) => address.hostPort
        case None => selfAddress
      }

      hostPort
    }
}
