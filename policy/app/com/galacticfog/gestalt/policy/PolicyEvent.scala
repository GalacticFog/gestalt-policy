package com.galacticfog.gestalt.policy

import com.galacticfog.gestalt.meta.api.sdk.ResourceInstance
import play.api.libs.json.{JsValue, Json}

case class PolicyArgs( rule : ResourceInstance, payload : JsValue )

case class PolicyEvent( id : String, identity : String, timestamp : Long, event : String, action : String, args : PolicyArgs )

object PolicyEvent {
  implicit val policyArgsFormat = Json.format[PolicyArgs]
  implicit val policyEventFormat = Json.format[PolicyEvent]
}
