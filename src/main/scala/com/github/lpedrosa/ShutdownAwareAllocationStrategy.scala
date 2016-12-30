package com.github.lpedrosa

import scala.collection.immutable
import scala.concurrent.Future

import akka.actor.{ActorRef, ActorSystem}
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
}

object ShutdownAwareAllocationStrategy {

  def defaultWithBlacklist(system: ActorSystem, blacklistService: BlacklistService): ShutdownAwareAllocationStrategy = {
    val settings = ClusterShardingSettings(system)

    // akka uses the LeastShardAllocationStrategy by default, we just want to decorate it
    val delegate = new LeastShardAllocationStrategy(
      settings.tuningParameters.leastShardAllocationRebalanceThreshold,
      settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance)

    new ShutdownAwareAllocationStrategy(delegate, blacklistService)
  }

}

class ShutdownAwareAllocationStrategy(delegate: ShardAllocationStrategy, 
                                      blacklistService: BlacklistService) extends ShardAllocationStrategy {

    private val log = LoggerFactory.getLogger(classOf[ShutdownAwareAllocationStrategy])

    override def allocateShard(
      requester: ActorRef, 
      shardId: ShardId, 
      currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {

      // filter out shards marked for shutdown
      val liveShardAllocations = filterShards(currentShardAllocations)
      delegate.allocateShard(requester, shardId, currentShardAllocations)
    }

    override def rebalance(
      currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
      rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {
        // do nothing special here
        // TODO(lpedrosa): should take into account shards that are shutting down, here as well
        delegate.rebalance(currentShardAllocations, rebalanceInProgress)
    }

    private def filterShards(currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]) = {
      currentShardAllocations.keysIterator.foreach(actor => log.info("Actor with shards: {}", actor.path.address.hostPort))
      currentShardAllocations
    }
}
