package com.galacticfog.gestalt.policy

import java.io.File
import java.net.URL

import akka.actor.{UnhandledMessage, ActorSystem}
import com.galacticfog.gestalt.meta.api.sdk.{Meta, BasicCredential, HostConfig}
import com.galacticfog.gestalt.policy.actors.{FactoryActor, UnhandledMessageActor}
import play.api.Logger

class PolicyFramework( metaHost : String, metaPort : Int, metaUser : String, metaPassword : String ) {

  def test() : String = {
    "boop"
  }
}

case class RabbitConfig( exchangeName : String, routeKey : String, hostName : String, port : Int )

object PolicyFramework {

  //TODO : remove defaults for local config and throw errors
  val hostName      = sys.env.getOrElse( "RABBIT_HOST", "192.168.200.20" )
  val port          = sys.env.getOrElse( "RABBIT_PORT", "10000" ).toInt
  val exchangeName  = sys.env.getOrElse( "RABBIT_EXCHANGE", "test-exchange" )
  val routeKey      = sys.env.getOrElse( "RABBIT_ROUTE", "policy" )
  val minWorkers    = sys.env.getOrElse( "POLICY_MIN_WORKERS", "1" ).toInt
  val maxWorkers    = sys.env.getOrElse( "POLICY_MAX_WORKERS", "3" ).toInt
  val metaHost      = sys.env.getOrElse( "META_HOST", "meta.dev2.galacticfog.com" )
  val metaProtocol  = sys.env.getOrElse( "META_PROTOCOL", "http" )
  val metaPort      = sys.env.getOrElse( "META_PORT", "14374" ).toInt
  val metaUser      = sys.env.getOrElse( "META_USER", "root" )
  val metaPassword  = sys.env.getOrElse( "META_PASSWORD", "letmein" )

  Logger.debug( "Connecting to " + hostName + ":" + port )

  val system = ActorSystem("PolicyActorSystem")
  val rabbitConfig = new RabbitConfig( exchangeName, routeKey, hostName, port )
  val metaConfig = new HostConfig( metaProtocol, metaHost, None, 10, Some( new BasicCredential( metaUser, metaPassword ) ) )
  val factory = system.actorOf( FactoryActor.props( rabbitConfig, metaConfig, minWorkers, maxWorkers ), "factory-actor" )

  val unhandledActor = system.actorOf( UnhandledMessageActor.props(), "unhandled-message-actor" )
  system.eventStream.subscribe(unhandledActor, classOf[UnhandledMessage])

  def init() : PolicyFramework = {

    new PolicyFramework( metaHost, metaPort, metaUser, metaPassword )
  }

}
