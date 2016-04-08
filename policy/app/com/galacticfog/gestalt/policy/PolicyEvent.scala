package com.galacticfog.gestalt.policy

import play.api.libs.json.{Json, JsValue}

case class EventContext( eventName : String, meta : String, workspace : String, environment : String, org : String, resourceId : String, resourceType : String )

case class LambdaArgs( resource : JsValue )

case class PolicyEvent( eventContext : EventContext, lambdaArgs : LambdaArgs )

object PolicyEvent {
  implicit val eventContextFormat = Json.format[EventContext]
  implicit val lambdaArgsFormat = Json.format[LambdaArgs]
  implicit val policyEventFormat = Json.format[PolicyEvent]
}
