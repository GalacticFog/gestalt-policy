package com.galacticfog.gestalt.policy

import com.galacticfog.gestalt.meta.api.sdk.{ResourceInstance, ResourceLink}
import com.galacticfog.gestalt.utils.json.JsonUtils._
import play.api.libs.json.{Reads, JsValue}

import scala.reflect.ClassTag

case class PolicyRule(
  actions : Seq[String],
  defined_at : ResourceLink,
  eval_logic : String,
  filter : String,
  lambda : ResourceLink,
  parent : ResourceLink
)

object PolicyRule {

  def getProp[A]( key : String, props : Map[String, JsValue] )(implicit reads: Reads[A], classTag: ClassTag[A]) : A = {

    val prop = props.get( key ).get
    parseAs[A]( prop, "Could not find property named : " + key )
  }

  def make( instance : ResourceInstance ) : PolicyRule = {
    val props = instance.properties.get
    new PolicyRule(
      actions     = getProp[Seq[String]]( "actions", props ),
      defined_at  = getProp[ResourceLink]( "defined_at", props ),
      eval_logic  = getProp[String]( "eval_logic", props ),
      filter      = getProp[String]( "filter", props ),
      lambda      = getProp[ResourceLink]( "lambda", props ),
      parent      = getProp[ResourceLink]( "parent", props )
    )
  }

}
