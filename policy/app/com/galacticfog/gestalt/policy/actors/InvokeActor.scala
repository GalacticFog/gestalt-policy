package com.galacticfog.gestalt.policy.actors

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.PolicyEvent
import com.galacticfog.gestalt.policy.actors.PolicyMessages.{StopConsumerWorker, IncomingEvent}
import com.rabbitmq.client.{Channel, Envelope}
import play.api.Logger

class InvokeActor( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope ) extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  override def preStart(): Unit = {
    Logger.debug( s"preStart( $id )" )
  }

  val handleRequests : Receive = {

    case IncomingEvent( event, channel, envelope ) => {
      Logger.debug( s"Consumer( $id ) - IncomingEvent : " + event.name  )

      //TODO : do the damn thing instead of sleeping (simulating work)
      Thread.sleep( 4000 )


      context.parent ! StopConsumerWorker( id )
    }
  }
}

object InvokeActor {
  def props( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope ) : Props = Props(
    new InvokeActor( id, event, channel, envelope )
  )
}
