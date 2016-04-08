package com.galacticfog.gestalt.policy.actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.{WebClient, PolicyEvent}
import com.galacticfog.gestalt.policy.actors.PolicyMessages._
import com.rabbitmq.client.{Channel, Envelope}
import play.api.Logger
import play.api.libs.json.Json

class InvokeActor( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope, binder : ActorRef ) extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  override def preStart(): Unit = {
    Logger.trace( s"preStart( $id )" )
  }

  val lambdaHost = sys.env.getOrElse( "LAMBDA_HOST", "localhost" )
  val lambdaPort = sys.env.getOrElse( "LAMBDA_PORT", "9001" ).toInt
  val lambdaUser = sys.env.getOrElse( "LAMBDA_USER", "root" )
  val lambdaPassword = sys.env.getOrElse( "LAMBDA_PASSWORD", "letmein" )
  val builder = new com.ning.http.client.AsyncHttpClientConfig.Builder()
  val client = new play.api.libs.ws.ning.NingWSClient(builder.build())
  val wsClient = WebClient( client, "http", lambdaHost, lambdaPort, lambdaUser, lambdaPassword )

  val handleRequests : Receive = {

    case IncomingEvent( event, channel, envelope ) => {
      Logger.trace( s"Consumer( $id ) - IncomingEvent : " + event.eventContext.eventName )

      //TODO : we probably need the context as well
      binder ! LookupLambda( event )
    }

    case FoundLambda( event, lambdaId ) => {

      //TODO : get the actual ID form the binder
      val url = "/lambdas/" + lambdaId + "/invoke"
      //TODO : get the actual payload out of the event
      val payload = Json.parse( "{ \"thing\" : \"otherThing\" }" )
      val result = wsClient.easyPost( url, payload )

      //context.parent ! StopConsumerWorker( id )
    }

    case LambdaNotFound( event ) => {
      ???
    }
  }
}

object InvokeActor {
  def props( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope, binder : ActorRef ) : Props = Props(
    new InvokeActor( id, event, channel, envelope, binder )
  )
}
