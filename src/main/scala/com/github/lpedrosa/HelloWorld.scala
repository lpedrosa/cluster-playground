package com.github.lpedrosa

import akka.actor._

case object GreeterApi {
  case object Greet
  case class Greeting(message: String)
}

class Greeter extends Actor with ActorLogging {
  import GreeterApi._

  def receive = {
    case Greet => sender ! Greeting("Hello World")
  }
}


object HelloWorld extends App {

  import akka.pattern.ask
  import akka.util.Timeout

  import scala.concurrent.Await
  import scala.concurrent.duration._

  val system = ActorSystem("hello")

  val greeter = system.actorOf(Props[Greeter], "greeter")

  implicit val timeout = Timeout(10 seconds)
  
  val reply = greeter ? GreeterApi.Greet

  val greeting = Await.result(reply, timeout.duration)
    .asInstanceOf[GreeterApi.Greeting]

  println(s"Actor replied with: $greeting")

  val hasTerminated = system.terminate()
  Await.result(hasTerminated, timeout.duration)
}
