package com.galacticfog.gestalt.policy.actors

import java.util.UUID

import akka.actor
import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.actors.PolicyMessages.{UpdateConsumerWorkers, CheckWorkers, IncomingEvent}
import scala.concurrent.ExecutionContext.Implicits.global
import com.rabbitmq.client.{Channel, ConnectionFactory, Connection}
import scala.concurrent.duration._
import play.api.Logger

class FactoryActor(

  exchangeName : String,
  routeKey : String,
  hostName : String,
  port : Int,
  minWorkers : Int,
  maxWorkers : Int

) extends Actor with ActorLogging {

  val actorMap    = scala.collection.mutable.Map[String, (ActorRef, Int)]()
  val connection  = getConnection()
  val channel     = connection.createChannel
  val exchange    = channel.exchangeDeclare( exchangeName, "direct" )
  val queueName   = channel.queueDeclare.getQueue

  val MAX_CONSUMER_WORKERS = 5

  //bind our queue to the exchage
  channel.queueBind( queueName, exchangeName, routeKey )

  def getConnection() : Connection = {
    val factory = new ConnectionFactory()
    factory.setHost( hostName )
    factory.setPort( port )
    factory.newConnection()
  }

  def receive = LoggingReceive { handleRequests }

  private val TICK_TIME = 30 seconds

  override def preStart() = {
    context.system.scheduler.scheduleOnce( 1 second, self, CheckWorkers )
  }

  val handleRequests : Receive = {

    case CheckWorkers => {

      //scale up to the minimum number of workers
      if( actorMap.size < minWorkers ) {
        for ( i <- actorMap.size to ( minWorkers - 1 ) ) {
          val id = UUID.randomUUID.toString
          val actor = newWorkerActor( i, id, channel, queueName )
          actorMap += ( id -> (actor,0) )
        }
      }

      //if all the workers are at their max consumer workers consider scaling up
      if(
        actorMap.filter{ entry =>
          entry._2._2 == MAX_CONSUMER_WORKERS
        }
          ==
          maxWorkers )
      {
        Logger.debug( "WOKERS MAXXED OUT SCALE UP!!!" )
      }

      //TODO : check the worker queue size and decide to scale up
      context.system.scheduler.scheduleOnce( TICK_TIME, self, CheckWorkers )
    }

    case UpdateConsumerWorkers( id, num ) => {
      Logger.debug( s"UpdateConsumerWorkers( $id, $num )")
      val entry = actorMap.get( id ).getOrElse {
        throw new Exception( "ACTOR MAP OUT OF SYNC WITH REALITY?? - is this a race condition?" )
      }

      //update the actor count
      actorMap(id) = (entry._1, num)
    }

  }

  def newWorkerActor( n : Int, id : String, channel : Channel, queueName : String ) = {
    Logger.debug( s"newConsumerActor(( $n )" )
    context.actorOf( ConsumerActor.props( id, channel, queueName, MAX_CONSUMER_WORKERS ), name = s"consumer-actor-$n" )
  }

}

object FactoryActor {
  def props(
    exchangeName : String,
    routeKey : String,
    hostName : String,
    port : Int,
    minWorkers : Int,
    maxWorkers : Int
  ) : Props = Props( new FactoryActor(
      exchangeName,
      routeKey,
      hostName,
      port,
      minWorkers,
      maxWorkers
    )
  )
}
