package com.galacticfog.gestalt.policy

import akka.actor.{UnhandledMessage, ActorSystem}
import com.galacticfog.gestalt.policy.actors.{FactoryActor, UnhandledMessageActor}

class PolicyFramework {



}

object PolicyFramework {

  //TODO : remove defaults for local config and throw errors
  val hostName      = sys.env.getOrElse( "rabbitHost", "192.168.200.20" )
  val port          = sys.env.getOrElse( "rabbitPort", 10000 ).asInstanceOf[Int]
  val exchangeName  = sys.env.getOrElse( "exchangeName", "test-exchange" )
  val routeKey      = sys.env.getOrElse( "routeKey", "policy" )
  val minWorkers    = sys.env.getOrElse( "minWorkers", 1 ).asInstanceOf[Int]
  val maxWorkers    = sys.env.getOrElse( "maxWorkers", 3 ).asInstanceOf[Int]

  val system = ActorSystem("PolicyActorSystem")
  val factory = system.actorOf( FactoryActor.props( exchangeName, routeKey, hostName, port, minWorkers, maxWorkers ), "factory-actor" )

  val unhandledActor = system.actorOf( UnhandledMessageActor.props(), "unhandled-message-actor" )
  system.eventStream.subscribe(unhandledActor, classOf[UnhandledMessage])

  def init() : PolicyFramework = {

    new PolicyFramework
  }

}
