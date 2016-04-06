package com.galacticfog.gestalt.policy.actors

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.pattern.ask
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.PolicyEvent
import com.galacticfog.gestalt.policy.actors.PolicyMessages._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger
import com.rabbitmq.client._
import play.api.libs.json.Json

import scala.concurrent.duration.Duration

class ConsumerActor( id : String, channel : Channel, queueName : String, maxWorkers : Int ) extends Actor with ActorLogging {

  implicit val timeout = Timeout(100.days)

  val consumer = new DefaultConsumer(channel) {
    override def handleDelivery( consumerTag : String, envelope : Envelope, properties : AMQP.BasicProperties, body : Array[Byte] ) : Unit = {

      //TODO : this try may be unnecessary since we do the work in the actor no
      try {
        val message = new String(body, "UTF-8")
        Logger.trace(" [x] Received '" + envelope.getRoutingKey() + "': tag :'" + envelope.getDeliveryTag + "':'" + message + "'")

        //TODO : is this right?  We don't really care about the result, we just need to wait until we farm out the job
        val result = Await.result( self ? ConsumerEvent( message, channel, envelope ), Duration( 30, TimeUnit.SECONDS ) )
      }
      catch {
        case ex : Exception => {
          val errorInfo = "CONSUMER FAILED : " + ex.getMessage + "\n" + ex.getStackTraceString
          self ! ConsumerError( errorInfo )
        }
      }
    }
  }

  val queue = channel.basicConsume( queueName, false, consumer )
  val actorMap = scala.collection.mutable.Map[ String, ActorRef ]()

  def numWorkers = actorMap.size

  def receive = LoggingReceive { handleRequests }

  override def preStart(): Unit = {
    Logger.debug( s"preStart( $id )" )
  }

  val handleRequests : Receive = {

    case StopConsumerWorker( id ) => {
      Logger.debug( "StopConsumerActor( " + id + " )" )
      val actor = actorMap.get( id ).get
      context.system.stop( actor )
      actorMap -= id
      context.parent ! UpdateConsumerWorkers( this.id, actorMap.size )
    }

    case ConsumerEvent( msg, channel, envelope ) => {


      if( actorMap.size == maxWorkers ) {
        Logger.trace( "Reject Message - too many workers" )
        channel.basicNack( envelope.getDeliveryTag, false, true )
        sender ! "done"
      }
      else {
        Logger.debug( s"ActorMap Size( ${this.id} ) : " + actorMap.size )
        val event = Json.parse( msg ).validate[PolicyEvent] getOrElse {
          throw new Exception( "Failed to parse Policy event form event queue" )
        }

        val actorId = UUID.randomUUID.toString
        val actor = newInvokeActor( actorId, actorId, event, channel, envelope )
        actorMap += ( actorId -> actor )
        context.parent ! UpdateConsumerWorkers( id, actorMap.size )
        actor ! IncomingEvent( event, channel, envelope )
        channel.basicAck( envelope.getDeliveryTag, false )
        sender ! "done"
      }
    }

    case ConsumerError( info ) => {

      //what to do here?
      Logger.debug( "received error : " + info )
      //TODO : is this the right thing?
      throw new Exception( info )

    }
  }

  def newInvokeActor( n : String, id : String, event : PolicyEvent, channel : Channel, envelope : Envelope ) = {
    Logger.debug( s"newInvokeActor(( $n )" )
    context.actorOf( InvokeActor.props( id, event, channel, envelope ), name = s"invoke-actor-$n" )
  }
}

object ConsumerActor {
  def props( id : String, channel : Channel, queueName : String, maxWorkers : Int ) : Props = Props( new ConsumerActor( id, channel, queueName, maxWorkers ) )
}