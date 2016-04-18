package com.galacticfog.gestalt.policy.actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.lambda.io.domain.LambdaEvent
import com.galacticfog.gestalt.lambda.io.domain.LambdaEvent._
import com.galacticfog.gestalt.policy.{WebClient, PolicyEvent}
import com.galacticfog.gestalt.policy.PolicyEvent._
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

      binder ! LookupLambda( event )
    }

    case FoundLambda( event, lambdaIds ) => {


      lambdaIds.foreach { lambdaId =>
        try {
          val url = "/lambdas/" + lambdaId + "/invoke"
          val payload = new LambdaEvent( event.eventContext.eventName, Json.toJson( event ) )
          val result = wsClient.easyPost( url, Json.toJson( payload ) )
        }
        catch {
          case ex : Exception => {
            //TODO : do anything else?  We don't want to stop processing any subsequent lambdas
            ex.printStackTrace
          }
        }
      }

      context.parent ! StopConsumerWorker( id )
    }

    case LambdaNotFound( event ) => {
      throw new Exception( s"Lambda not found for meta context : org (${event.eventContext.org}) - workspace (${event.eventContext.workspace}) - env (${event.eventContext.environment}) ")
    }
  }
}

object InvokeActor {
  def props( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope, binder : ActorRef ) : Props = Props(
    new InvokeActor( id, event, channel, envelope, binder )
  )
}
