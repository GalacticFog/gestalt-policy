package com.galacticfog.gestalt.policy.actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.lambda.io.domain.LambdaEvent
import com.galacticfog.gestalt.lambda.io.domain.LambdaEvent._
import com.galacticfog.gestalt.meta.api.sdk.ResourceLink
import com.galacticfog.gestalt.policy.{WebClient, PolicyEvent}
import com.galacticfog.gestalt.policy.PolicyEvent._
import com.galacticfog.gestalt.policy.actors.PolicyMessages._
import com.rabbitmq.client.{Channel, Envelope}
import play.api.Logger
import play.api.libs.json.Json

class InvokeActor( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope ) extends Actor with ActorLogging {

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
      Logger.trace( s"Consumer( $id ) - IncomingEvent : " + event.event )

      try {

        val lambdaId = event.args.rule.properties.get.get( "lambda" ) match {
          case Some(l) => {

            val link = l.validate[ResourceLink].getOrElse{
              throw new Exception( "Lambda resource is no longer a ResourceLink, something has changed in the rendering" )
            }

            link.id
          }
          case None => {
            throw new Exception( "Missing 'lambda' field from properties on rule." )
          }
        }

        val url = "/lambdas/" + lambdaId + "/invoke"
        val payload = new LambdaEvent( event.event, event.args.payload )
        val result = wsClient.easyPost( url, Json.toJson( payload ) )

      }
      catch {
        case ex: Exception => {
          //TODO : do anything else?  We don't want to stop processing any subsequent lambdas
          ex.printStackTrace
        }
      }

      context.parent ! StopConsumerWorker( id )
    }
  }
}

object InvokeActor {
  def props( id : String, event : PolicyEvent, channel : Channel, envelope : Envelope ) : Props = Props(
    new InvokeActor( id, event, channel, envelope )
  )
}
