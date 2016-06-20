package com.galacticfog.gestalt.policy.actors

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.meta.api.sdk.{ResourceInstance, Meta, HostConfig}
import com.galacticfog.gestalt.policy.{PolicyRule, PolicyEvent}
import com.galacticfog.gestalt.policy.actors.PolicyMessages._
import com.rabbitmq.client.{Channel, Envelope}
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class BindingActor( id : String, metaConfig : HostConfig ) extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  var environmentMap = collection.mutable.Map[ String, String ]()
  var workspaceMap = collection.mutable.Map[ String, String ]()

  val CHECK_DURATION = sys.env.getOrElse( "BINDING_UPDATE_SECONDS", "120" ).toInt.seconds
  val meta = new Meta( metaConfig )

  override def preStart(): Unit = {
    Logger.debug( s"preStart( $id )" )
    context.system.scheduler.scheduleOnce( 1 second, self, RepopulateMap )
  }

  val handleRequests : Receive = {

    case LookupLambda( event ) => {
      Logger.trace( s"LookupLambda( ${event.eventContext.eventName}} )")

      //TODO : lookup the lambda in the map once it's populated

      val workKey = event.eventContext.org + "." + event.eventContext.workspace + "." + event.eventContext.eventName
      val envKey = event.eventContext.org + "." + event.eventContext.environment + "." + event.eventContext.eventName

      val workLambda = workspaceMap.get( workKey )
      val envLambda = environmentMap.get( envKey )

      val lambdaEvent = (workLambda,envLambda) match {
        case (Some(s), Some(t)) => FoundLambda( event, Seq(s, t) )
        case (Some(s), None) => FoundLambda( event, Seq(s) )
        case (None, Some(s)) => FoundLambda( event, Seq(s) )
        case (None, None) => LambdaNotFound( event )
      }

      sender ! lambdaEvent
    }

    case RepopulateMap => {
      Logger.debug( s"RepopulateMap")

      //TODO : this is still leaking memory pretty good ~500K every execution
      printMemStats
      val list = meta.topLevelRules match {
        case Success(s) => s
        case Failure(ex) => {
          ex.printStackTrace
          throw new Exception( ex.getMessage )
        }
      }
      printMemStats

      //val rules = list.filter{ x => list.count( _.id == x.id ) == 1 }.map( PolicyRule.make(_) )
      //TODO : this is temporary, there will be no dupes in the futue
      val filtered = dedupe( list.toList )


      //NOTE : this is all an attempt to do a LEFT OUTER JOIN with the maps I fetch, but couldn't find a good
      //       way with the scala collection sets

      val rules = filtered.map( PolicyRule.make(_) )

      val ( envMap, workMap ) = processRules( rules )

      val envMapCommon = envMap.keySet.intersect( environmentMap.keySet ).map{ key =>
        ( key -> envMap.get( key ).get )
      }

      val envMapNew = envMap.keySet.diff( environmentMap.keySet ).map{ key =>
        ( key -> envMap.get( key ).get )
      }

      val workMapCommon = workMap.keySet.intersect( workspaceMap.keySet ).map{ key =>
        ( key -> workMap.get( key ).get )
      }

      val workMapNew = workMap.keySet.diff( workspaceMap.keySet ).map{ key =>
        ( key -> workMap.get( key ).get )
      }

      environmentMap = scala.collection.mutable.Map() ++ envMapCommon ++ envMapNew
      workspaceMap = scala.collection.mutable.Map() ++ workMapCommon ++ workMapNew

      Logger.debug( "Env Map (" )
      environmentMap.foreach{ entry =>
        Logger.debug( "\t( " + entry._1 + " -> " + entry._2 + " )" )
      }
      Logger.debug( ")")
      Logger.debug( "Workspace Map (" )
      workspaceMap.foreach{ entry =>
        Logger.debug( "\t( " + entry._1 + " -> " + entry._2 + " )" )
      }
      Logger.debug( ")")

      context.system.scheduler.scheduleOnce( CHECK_DURATION, self, RepopulateMap )

      //TODO : this shouldn't be necessary, let's see if it helps
      System.gc
    }
  }

  def printMemStats(): Unit = {
    val mb = (1024*1024).toDouble
    val runtime = Runtime.getRuntime
    Logger.debug("** Used Memory:  " + (runtime.totalMemory - runtime.freeMemory) / mb)
    //Logger.debug("** Free Memory:  " + runtime.freeMemory / mb)
    //Logger.debug("** Total Memory: " + runtime.totalMemory / mb)
    //Logger.debug("** Max Memory:   " + runtime.maxMemory / mb)
  }

  def dedupe( elements : List[ResourceInstance] ) : List[ResourceInstance] = {
    if (elements.isEmpty)
      elements
    else
      elements.head :: dedupe(for (x <- elements.tail if x.id != elements.head.id) yield x)
  }

  def processRules( rules : Seq[PolicyRule] ) = {

    val envMap = scala.collection.mutable.Map[ String, String ]()
    val workMap = scala.collection.mutable.Map[ String, String ]()

    rules.foreach{ rule =>
              Logger.trace( "Processing Rule : " + rule.defined_at )
              processRule( rule, envMap, workMap )
            }

    (envMap, workMap)
  }

  def processRule( rule : PolicyRule, envMap : scala.collection.mutable.Map[String,String], workMap : scala.collection.mutable.Map[String,String] ) = {

    if( rule.defined_at.href.get.toString.contains( "environments" ) )
    {
      //this assumes the href is constructed like so /orgs/{id}/environments/{id}

      val envExtractor = """.*/environments/(.*?)(?:/|$).*""".r
      val envExtractor( envId ) = rule.defined_at.href.get.toString
      //Logger.debug( "ENV : " + envId )

      val keyBase = rule.orgId + "." + envId
      rule.actions.foreach{ action =>
        val key = keyBase + "." + action
        //environmentMap += ( key -> rule.lambda.id )
        envMap( key ) = rule.lambda.id
      }
    }
    else if( rule.defined_at.href.get.toString.contains( "workspaces" ) )
    {
      //this assumes the href is constructed like so /orgs/{id}/workspaces/{id}

      val workExtractor = """.*/workspaces/(.*?)(?:/|$).*""".r
      val workExtractor( workId ) = rule.defined_at.href.get.toString
      //Logger.debug( "WORK : " + workId )

      val keyBase = rule.orgId + "." + workId
      rule.actions.foreach{ action =>
        val key = keyBase + "." + action
        workMap( key ) = rule.lambda.id
      }
    }
    else
    {
      Logger.trace( "ignoring ORG RULE per Anthony Skipper" )
    }
  }
}

object BindingActor {
  def props( id : String, metaConfig : HostConfig ) : Props = Props( new BindingActor( id, metaConfig ) )
}
