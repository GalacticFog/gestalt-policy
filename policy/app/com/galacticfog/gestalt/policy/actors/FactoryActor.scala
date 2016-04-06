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

  private val TICK_TIME = 5 seconds

  override def preStart() = {
    context.system.scheduler.scheduleOnce( 1 second, self, CheckWorkers )
  }

  val handleRequests : Receive = {

    case CheckWorkers => {

      Logger.debug( "Checking workers : " )
      actorMap.foreach { entry =>
        Logger.debug( "actor : " + entry._1 + " -> " + entry._2._2 )
      }

      //scale up to the minimum number of workers
      if( actorMap.size < minWorkers ) {
        for ( i <- actorMap.size until minWorkers ) {
          val id = UUID.randomUUID.toString
          val actor = newWorkerActor( id, id, channel, queueName )
          actorMap += ( id -> (actor,0) )
        }
      }

      //if all the workers are at their max consumer workers consider scaling up
      val numFullWorkers =  actorMap.filter{ entry => entry._2._2 == MAX_CONSUMER_WORKERS }.size
      if( numFullWorkers > 0 )
      {
        Logger.debug( "WOKERS MAXXED OUT SCALE UP!!!" )
        if( numFullWorkers == maxWorkers )
        {
          Logger.debug( "MAX workers reached - considering allowing more" )
        }
        else
        {
          val actorId = UUID.randomUUID.toString
          val actor = newWorkerActor( actorId, actorId, channel, queueName )
          actorMap += ( actorId -> (actor, 0))
        }
      }

      //TODO : check times since workers were taxxed and scale down
      val numEmptyWorkers = actorMap.filter{ entry => entry._2._2 == 0 }.size
      if( numEmptyWorkers > 1 && actorMap.size > minWorkers )
      {
        Logger.debug( "WORKERS IDLE - SCALING DOWN...")
        val entry = actorMap.filter{ entry => entry._2._2 == 0 }.head
        context.system.stop( entry._2._1 )
        actorMap -= entry._1
      }

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

  def newWorkerActor( n : String, id : String, channel : Channel, queueName : String ) = {
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
