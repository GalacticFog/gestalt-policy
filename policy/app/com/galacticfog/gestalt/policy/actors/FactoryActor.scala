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
import com.rabbitmq.client._
import scala.concurrent.duration._
import play.api.Logger

class FactoryActor(

  rabbitConfig : RabbitConfig,
  metaConfig : HostConfig,
  minWorkers : Int,
  maxWorkers : Int

) extends Actor with ActorLogging {

  val actorMap    = scala.collection.mutable.Map[String, (ActorRef, Int)]()
  var connection  = getConnection()
  var channel     = connection.createChannel
  val exchange    = channel.exchangeDeclare( rabbitConfig.exchangeName, "direct" )
  val queueName   = "worker-queue"
  val queue       = channel.queueDeclare( queueName, true, false, false, null )

  channel.queueBind( queueName, rabbitConfig.exchangeName, rabbitConfig.routeKey )


  val MAX_CONSUMER_WORKERS = sys.env.getOrElse( "MAX_CONSUMER_WORKERS", "12" ).toInt
  val TICK_TIME = sys.env.getOrElse( "WORKER_TICK_TIME_SECONDS", "3" ).toInt.seconds
  val CONNECTION_CHECK_TIME = sys.env.getOrElse( "CONNECTION_CHECK_TIME_SECONDS", "60" ).toInt.seconds


  def getConnection() : Connection = {

    Logger.debug( "Rabbit Connection : \n" +
      "\t host     : " + rabbitConfig.hostName + "\n" +
      "\t port     : " + rabbitConfig.port + "\n" +
      "\t exchange : " + rabbitConfig.exchangeName + "\n" +
      "\t route    : " + rabbitConfig.routeKey + "\n" )

    val factory = new ConnectionFactory()
    factory.setHost( rabbitConfig.hostName )
    factory.setPort( rabbitConfig.port )
    //0 is infinite
    factory.setConnectionTimeout(0)
    factory.setAutomaticRecoveryEnabled( true )
    factory.setRequestedHeartbeat( 10 )

    val con = factory.newConnection()
    con.addShutdownListener(
      new ShutdownListener()
      {
        override def shutdownCompleted(cause : ShutdownSignalException) : Unit =
        {
          Logger.warn( "CONNECTION SHUTDOWN : " )
          Logger.warn( " - reason : " + cause.getReason )
          Logger.warn( " - is hard : " + cause.isHardError )
          Logger.warn( " - is caused by app : " + cause.isInitiatedByApplication )
          Logger.warn( " - message : " + cause.getMessage )
          Logger.warn (" - stack trace : " + cause.getStackTraceString )

          self ! CheckConnection
        }
      }
    )
    con
  }

  def receive = LoggingReceive { handleRequests }


  override def preStart() = {


    //context.system.scheduler.scheduleOnce( 1 second, self, CheckConnection )
    context.system.scheduler.scheduleOnce( 1 second, self, CheckWorkers )
  }

  val handleRequests : Receive = {

    case CheckConnection => {
      Logger.trace( "Checking Connection" )

      if( !connection.isOpen )
      {

        Logger.debug( "CONNECTION DIED - RESTARTING" )
        //we have to kill the connection and then restart all the consumers
        connection = getConnection()

        //now restart all of the workers
        actorMap.foreach{ entry =>
          entry._2._1 ! ShutdownConsumer
        }
      }

      //context.system.scheduler.scheduleOnce( CONNECTION_CHECK_TIME, self, CheckConnection )
    }

    case CheckWorkers => {

      Logger.trace( "Checking workers : " )
      actorMap.foreach { entry =>
        Logger.trace( "actor : " + entry._1 + " -> " + entry._2._2 )
      }

      //scale up to the minimum number of workers
      if( actorMap.size < minWorkers ) {
        for ( i <- actorMap.size until minWorkers ) {
          val id = UUID.randomUUID.toString
          val actor = newConsumerActor( id, id, connection.createChannel, queueName )
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
          val actor = newConsumerActor( actorId, actorId, connection.createChannel, queueName )
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

  def newConsumerActor( n : String, id : String, channel : Channel, queueName : String ) = {
    Logger.trace( s"newConsumerActor(( $n )" )
    context.actorOf( ConsumerActor.props( id, channel, queueName, MAX_CONSUMER_WORKERS ), name = s"consumer-actor-$n" )
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
