package com.galacticfog.gestalt.policy

import java.io.File
import java.net.URL

import akka.actor.{UnhandledMessage, ActorSystem}
import com.galacticfog.gestalt.meta.api.sdk.{Meta, BasicCredential, HostConfig}
import com.galacticfog.gestalt.policy.actors.{FactoryActor, UnhandledMessageActor}
import play.api.Logger

class PolicyFramework {

  def test() : String = {
    "boop"
  }
}

case class RabbitConfig( exchangeName : String, routeKey : String, hostName : String, port : Int )

object PolicyFramework {

  //TODO : remove defaults for local config and throw errors
  val hostName      = sys.env.getOrElse( "RABBIT_SERVICE_HOST", "192.168.200.20" )
  val port          = sys.env.getOrElse( "RABBIT_SERVICE_PORT", "10000" ).toInt
  val exchangeName  = sys.env.getOrElse( "RABBIT_EXCHANGE", "test-exchange" )
  val routeKey      = sys.env.getOrElse( "RABBIT_ROUTE", "policy" )
  val minWorkers    = sys.env.getOrElse( "POLICY_MIN_WORKERS", "1" ).toInt
  val maxWorkers    = sys.env.getOrElse( "POLICY_MAX_WORKERS", "3" ).toInt

  Logger.debug( "Connecting to " + hostName + ":" + port )

  val system = ActorSystem("PolicyActorSystem")
  val rabbitConfig = new RabbitConfig( exchangeName, routeKey, hostName, port )
  val factory = system.actorOf( FactoryActor.props( rabbitConfig, minWorkers, maxWorkers ), "factory-actor" )

  val unhandledActor = system.actorOf( UnhandledMessageActor.props(), "unhandled-message-actor" )
  system.eventStream.subscribe(unhandledActor, classOf[UnhandledMessage])

  def init() : PolicyFramework = {

    new PolicyFramework()
  }

}
