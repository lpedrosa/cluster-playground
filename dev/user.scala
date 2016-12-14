import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import com.github.lpedrosa._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import java.util.concurrent.Executors

val consoleSystem = ActorSystem("consoleSystem")

implicit val defaulTimeout = Timeout(5 seconds)
implicit val defaultExecutionContext = {
  val executor = Executors.newFixedThreadPool(4)
  ExecutionContext.fromExecutorService(executor)
}
