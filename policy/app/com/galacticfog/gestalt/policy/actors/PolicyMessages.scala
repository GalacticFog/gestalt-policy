package com.galacticfog.gestalt.policy.actors

import com.galacticfog.gestalt.policy.PolicyEvent
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.Channel

object PolicyMessages {

  sealed trait PolicyMessage

  case class IncomingEvent( event : PolicyEvent, channel : Channel, envelope : Envelope ) extends PolicyMessage
  case object CheckWorkers

}
