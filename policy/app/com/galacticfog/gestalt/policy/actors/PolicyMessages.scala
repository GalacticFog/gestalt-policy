package com.galacticfog.gestalt.policy.actors

import com.galacticfog.gestalt.policy.PolicyEvent
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.Channel

object PolicyMessages {

  sealed trait PolicyMessage

  case class IncomingEvent( event : PolicyEvent, channel : Channel, envelope : Envelope ) extends PolicyMessage
  case object CheckWorkers extends PolicyMessage
  case object ShutdownConsumer extends PolicyMessage
  case class RemoveConsumer( id : String ) extends PolicyMessage

  case class ConsumerEvent( msg : String, channel : Channel, evnelope : Envelope ) extends PolicyMessage
  case class UpdateConsumerWorkers( id : String, num : Int ) extends PolicyMessage
  case class StopConsumerWorker( id : String ) extends PolicyMessage
  case class ConsumerError( msg : String ) extends PolicyMessage

  case class LookupLambda( event : PolicyEvent ) extends PolicyMessage
  case class FoundLambda( event : PolicyEvent, lambdaId : String ) extends PolicyMessage
  case class LambdaNotFound( event : PolicyEvent ) extends PolicyMessage
  case object RepopulateMap extends PolicyMessage
}
