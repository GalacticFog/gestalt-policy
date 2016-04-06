package com.galacticfog.gestalt.policy

import play.api.libs.json.{Json, JsValue}

case class PolicyEvent( name : String, data : JsValue )

object PolicyEvent {
  implicit val policyEventFormat = Json.format[PolicyEvent]
}
