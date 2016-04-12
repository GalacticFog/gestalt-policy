package com.galacticfog.gestalt.policy.actors

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor
import akka.pattern.ask
import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.meta.api.sdk.HostConfig
import com.galacticfog.gestalt.policy.RabbitConfig
import com.galacticfog.gestalt.policy.actors.PolicyMessages._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import com.rabbitmq.client.{Channel, ConnectionFactory, Connection}
import scala.concurrent.duration._
import play.api.Logger

class FactoryActor(

  rabbitConfig : RabbitConfig,
  metaConfig : HostConfig,
  minWorkers : Int,
  maxWorkers : Int

) extends Actor with ActorLogging {

  val actorMap    = scala.collection.mutable.Map[String, (ActorRef, Int)]()
  val connection  = getConnection()
  val channel     = connection.createChannel
  val exchange    = channel.exchangeDeclare( rabbitConfig.exchangeName, "direct" )
  val queueName   = "worker-queue"
  val queue       = channel.queueDeclare( queueName, true, false, false, null )
  val binder      = newBinderActor( UUID.randomUUID.toString, metaConfig )

  val MAX_CONSUMER_WORKERS = 12

  //bind our queue to the exchage
  channel.queueBind( queueName, rabbitConfig.exchangeName, rabbitConfig.routeKey )

  def getConnection() : Connection = {
    val factory = new ConnectionFactory()
    factory.setHost( rabbitConfig.hostName )
    factory.setPort( rabbitConfig.port )
    factory.setRequestedHeartbeat(300)
    factory.newConnection()
  }

  def receive = LoggingReceive { handleRequests }

  private val TICK_TIME = 3 seconds

  override def preStart() = {
    context.system.scheduler.scheduleOnce( 1 second, self, CheckWorkers )
  }

  val handleRequests : Receive = {

    case CheckWorkers => {

      Logger.trace( "Checking workers : " )
      actorMap.foreach { entry =>
        Logger.trace( "actor : " + entry._1 + " -> " + entry._2._2 )
      }

      //scale up to the minimum number of workers
      if( actorMap.size < minWorkers ) {
        for ( i <- actorMap.size until minWorkers ) {
          val id = UUID.randomUUID.toString
          val actor = newConsumerActor( id, id, connection.createChannel, queueName, binder )
          actorMap += ( id -> (actor,0) )
        }
      }

      //if all the workers are at their max consumer workers consider scaling up
      /*
      val numFullWorkers =  actorMap.filter{ entry => entry._2._2 == MAX_CONSUMER_WORKERS }.size
      if( numFullWorkers > 0 )
      */
      val totalWorkers = actorMap.foldLeft{0}{ (x,y) =>
        x + y._2._2
      }
      val pctFull = totalWorkers.toDouble / (actorMap.size * MAX_CONSUMER_WORKERS).toDouble
      Logger.trace( s"Actors(${actorMap.size}) - Workers($totalWorkers) - Pct($pctFull)")
      Logger.trace( s"Messages(${queue.getMessageCount}) - Consumers(${queue.getConsumerCount})" )

      if( pctFull > .50 )
      {
        Logger.debug( "WOKERS MAXXED OUT SCALE UP!!!" )
        if( actorMap.size == maxWorkers )
        {
          Logger.debug( "MAX workers reached - considering allowing more" )
        }
        else
        {
          val actorId = UUID.randomUUID.toString
          val actor = newConsumerActor( actorId, actorId, connection.createChannel, queueName, binder )
          actorMap += ( actorId -> (actor, 0))
        }
      }

      //TODO : check times since workers were taxxed and scale down
      val numEmptyWorkers = actorMap.filter{ entry => entry._2._2 == 0 }.size
      if( numEmptyWorkers > 1 && actorMap.size > minWorkers )
      {
        Logger.debug( "WORKERS IDLE - SCALING DOWN...")
        val entry = actorMap.filter{ entry => entry._2._2 == 0 }.head
        //TODO : it's still possible that the worker started chewing on a task right before we've asked it to shutdown
        entry._2._1 ! ShutdownConsumer
      }

      context.system.scheduler.scheduleOnce( TICK_TIME, self, CheckWorkers )
    }

    case RemoveConsumer( id ) => {
      Logger.trace( s"RemoveConsumer( $id )" )
      val entry = actorMap(id)
      context.system.stop( entry._1 )
      actorMap -= id
    }

    case UpdateConsumerWorkers( id, num ) => {
      Logger.trace( s"UpdateConsumerWorkers( $id, $num )")
      val entry = actorMap.get( id ).getOrElse {
        throw new Exception( s"ACTOR MAP OUT OF SYNC $id does not exist" )
      }

      //update the actor count
      actorMap(id) = (entry._1, num)
    }

  }

  def newConsumerActor( n : String, id : String, channel : Channel, queueName : String, binder : ActorRef ) = {
    Logger.trace( s"newConsumerActor(( $n )" )
    context.actorOf( ConsumerActor.props( id, channel, queueName, MAX_CONSUMER_WORKERS, binder ), name = s"consumer-actor-$n" )
  }

  def newBinderActor( id : String, metaConfig : HostConfig ) = {
    Logger.debug( s"newBinderActor(( $id )" )
    context.actorOf( BindingActor.props( id, metaConfig ), name = s"binding-actor-$id" )
  }

}

object FactoryActor {
  def props(
    rabbitConfig : RabbitConfig,
    metaConfig : HostConfig,
    minWorkers : Int,
    maxWorkers : Int
  ) : Props = Props( new FactoryActor(
      rabbitConfig,
      metaConfig,
      minWorkers,
      maxWorkers
    )
  )
}
