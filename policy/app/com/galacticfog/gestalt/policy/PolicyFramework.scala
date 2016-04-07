package com.galacticfog.gestalt.policy

import akka.actor.{UnhandledMessage, ActorSystem}
import com.galacticfog.gestalt.policy.actors.{FactoryActor, UnhandledMessageActor}

class PolicyFramework {



}

object PolicyFramework {

  //TODO : remove defaults for local config and throw errors
  val hostName      = sys.env.getOrElse( "RABBIT_HOST", "192.168.200.20" )
  val port          = sys.env.getOrElse( "RABBIT_PORT", "10000" ).asInstanceOf[String].toInt
  val exchangeName  = sys.env.getOrElse( "RABBIT_EXCHANGE", "test-exchange" )
  val routeKey      = sys.env.getOrElse( "RABBIT_ROUTE", "policy" )
  val minWorkers    = sys.env.getOrElse( "POLICY_MIN_WORKERS", "1" ).asInstanceOf[String].toInt
  val maxWorkers    = sys.env.getOrElse( "POLICY_MAX_WORKERS", "3" ).asInstanceOf[String].toInt

  val system = ActorSystem("PolicyActorSystem")
  val factory = system.actorOf( FactoryActor.props( exchangeName, routeKey, hostName, port, minWorkers, maxWorkers ), "factory-actor" )

  val unhandledActor = system.actorOf( UnhandledMessageActor.props(), "unhandled-message-actor" )
  system.eventStream.subscribe(unhandledActor, classOf[UnhandledMessage])

  def init() : PolicyFramework = {

    new PolicyFramework
  }

}
