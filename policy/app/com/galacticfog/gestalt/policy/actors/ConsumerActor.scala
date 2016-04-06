package com.galacticfog.gestalt.policy.actors

import java.util.UUID

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.PolicyEvent
import com.galacticfog.gestalt.policy.actors.PolicyMessages.IncomingEvent
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger
import com.rabbitmq.client._
import play.api.libs.json.Json

class ConsumerActor( id : String, channel : Channel, queueName : String ) extends Actor with ActorLogging {

  private val MAX_WORKERS = 5

  val consumer = new DefaultConsumer(channel) {
    override def handleDelivery( consumerTag : String, envelope : Envelope, properties : AMQP.BasicProperties, body : Array[Byte] ) : Unit = {
      val message = new String(body, "UTF-8")

      Logger.debug(" [x] Received '" + envelope.getRoutingKey() + "': tag :'" + envelope.getDeliveryTag + "':'" + message + "'")

      if( actorMap.size == 5 ) {
        channel.basicNack( envelope.getDeliveryTag, false, true )
      }
      else {
        val event = Json.parse( message ).validate[PolicyEvent] getOrElse {
          throw new Exception( "Failed to parse Policy event form event queue" )
        }

        self ! IncomingEvent( event, channel, envelope )

        //TODO : we may want to wait to ACK this until the policy has run successfully???
        channel.basicAck( envelope.getDeliveryTag, false )
      }

    }
  }

  println( "BASIC CONSUME" )
  val queue = channel.basicConsume( queueName, false, consumer )
  val actorMap = scala.collection.mutable.Map[ String, ActorRef ]()

  def receive = LoggingReceive { handleRequests }

  override def preStart(): Unit = {
    Logger.debug( "preStart()" )
  }

  val handleRequests : Receive = {

    case IncomingEvent( event, channel, envelope ) => {

      val actorId = UUID.randomUUID.toString
      val actor = newWorkerActor( actorMap.size, actorId, event, channel, envelope )
      actorMap += ( actorId -> actor )
      actor ! IncomingEvent( event, channel, envelope )
    }
  }

  def newWorkerActor( n : Int, id : String, event : PolicyEvent, channel : Channel, envelope : Envelope ) = {
    Logger.debug( s"newInvokeActor(( $n )" )
    context.actorOf( InvokeActor.props( id, event, channel, envelope ), name = s"invoke-actor-$n" )
  }
}

object ConsumerActor {
  def props( id : String, channel : Channel, queueName : String ) : Props = Props( new ConsumerActor( id, channel, queueName ) )
}
