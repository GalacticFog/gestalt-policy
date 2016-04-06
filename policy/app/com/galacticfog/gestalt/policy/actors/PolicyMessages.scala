package com.galacticfog.gestalt.policy.actors

import com.galacticfog.gestalt.policy.PolicyEvent
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.Channel

object PolicyMessages {

  sealed trait PolicyMessage

  case class IncomingEvent( event : PolicyEvent, channel : Channel, envelope : Envelope ) extends PolicyMessage
  case object CheckWorkers extends PolicyMessage

  case class ConsumerEvent( msg : String, channel : Channel, evnelope : Envelope ) extends PolicyMessage
  case class UpdateConsumerWorkers( id : String, num : Int ) extends PolicyMessage
  case class StopConsumerWorker( id : String ) extends PolicyMessage
  case class ConsumerError( msg : String ) extends PolicyMessage
}
