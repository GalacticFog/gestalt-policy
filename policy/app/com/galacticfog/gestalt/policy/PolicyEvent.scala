package com.galacticfog.gestalt.policy

import com.galacticfog.gestalt.meta.api.sdk.ResourceInstance
import play.api.libs.json.{JsValue, Json}

case class PolicyArgs( rule : ResourceInstance, payload : JsValue )

// ESS: Workaround for https://gitlab.com/galacticfog/gestalt-policy/issues/6 - error parsing timestamp field.  Converting to a String
// Since the timestamp doesn't otherwise appear to be used in this codebase.
//case class PolicyEvent( id : String, identity : String, timestamp : Long, event : String, action : String, args : PolicyArgs )
case class PolicyEvent( id : String, identity : String, timestamp : String, event : String, action : String, args : PolicyArgs )

object PolicyEvent {
  implicit val policyArgsFormat = Json.format[PolicyArgs]
  implicit val policyEventFormat = Json.format[PolicyEvent]
}
