package controllers

import com.galacticfog.gestalt.policy.PolicyFramework
import play.api._
import play.api.mvc._

object ApplicationController extends Controller {

  val service = PolicyFramework.init

  def index = Action {
    Ok( service.test )
  }

  def health = Action {
    Ok( "healthy" )
  }

}
