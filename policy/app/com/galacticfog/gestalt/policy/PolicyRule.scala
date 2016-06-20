package com.galacticfog.gestalt.policy

import com.galacticfog.gestalt.meta.api.sdk.{ResourceInstance, ResourceLink}
import com.galacticfog.gestalt.utils.json.JsonUtils._
import play.api.Logger
import play.api.libs.json.{Reads, JsValue}

import scala.reflect.ClassTag

case class PolicyRule(
  actions : Seq[String],
  defined_at : ResourceLink,
  eval_logic : Option[String],
  filter : Option[String],
  lambda : ResourceLink,
  parent : ResourceLink,
  orgId : String
)

object PolicyRule {

  def getProp[A]( key : String, props : Map[String, JsValue] )(implicit reads: Reads[A], classTag: ClassTag[A]) : Option[A] = {

    props.get( key ) match {
      case Some( s ) => {
        Some( parseAs[A]( s, "Could not find property named : " + key ) )
      }
      case None => {
        None
      }
    }
  }

  def make( instance : ResourceInstance ) : PolicyRule = {
    val props = instance.properties.get
    val orgId = instance.org.id
    new PolicyRule(
      actions     = getProp[Seq[String]]( "actions", props ).get,
      defined_at  = getProp[ResourceLink]( "defined_at", props ).get,
      eval_logic  = getProp[String]( "eval_logic", props ),
      filter      = getProp[String]( "filter", props ),
      lambda      = getProp[ResourceLink]( "lambda", props ).get,
      parent      = getProp[ResourceLink]( "parent", props ).get,
      orgId       = orgId
    )
  }

}
