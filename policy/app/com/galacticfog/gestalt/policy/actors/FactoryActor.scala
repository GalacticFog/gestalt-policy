package com.galacticfog.gestalt.policy.actors

import java.util.UUID

import akka.actor
import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.policy.actors.PolicyMessages.{CheckWorkers, IncomingEvent}
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

  val actorMap    = scala.collection.mutable.Map[String, ActorRef]()
  val connection  = getConnection()
  val channel     = connection.createChannel
  val exchange    = channel.exchangeDeclare( exchangeName, "direct" )
  val queueName   = channel.queueDeclare.getQueue

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
          actorMap += ( id -> actor )
        }
      }

      //TODO : check the worker queue size and decide to scale up
      context.system.scheduler.scheduleOnce( TICK_TIME, self, CheckWorkers )
    }

  }

  def newWorkerActor( n : Int, id : String, channel : Channel, queueName : String ) = {
    Logger.debug( s"newConsumerActor(( $n )" )
    context.actorOf( ConsumerActor.props( id, channel, queueName ), name = s"consumer-actor-$n" )
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
