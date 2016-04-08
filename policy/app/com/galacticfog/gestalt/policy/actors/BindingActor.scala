package com.galacticfog.gestalt.policy.actors

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.PolicyEvent
import com.galacticfog.gestalt.policy.actors.PolicyMessages._
import com.rabbitmq.client.{Channel, Envelope}
import play.api.Logger

class BindingActor( id : String ) extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  val bindingMap = collection.mutable.Map[ String, String ]()

  override def preStart(): Unit = {
    Logger.trace( s"preStart( $id )" )
  }

  val handleRequests : Receive = {

    case LookupLambda( event ) => {
      Logger.trace( s"LookupLambda( ${event.eventContext.eventName}} )")

      //TODO : lookup the lambda in the map once it's populated

      //if we find the lambda
      sender ! FoundLambda( event, "lambdaId" )

      //if we didn't find the lambda
      //sender ! LambdaNotFound( eventId )

    }

    case RepopulateMap => {
      Logger.trace( s"RepopulateMap")

    }
  }
}

object BindingActor {
  def props( id : String ) : Props = Props( new BindingActor( id ) )
}
