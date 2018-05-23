/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.sparta.serving.core.actor

import akka.actor.Actor
import akka.stream.ActorMaterializer
import com.stratio.sparta.serving.core.actor.EnvironmentCleanerActor.TriggerCleaning
import com.stratio.sparta.serving.core.utils.MarathonAPIUtils

class EnvironmentCleanerActor extends Actor {

  private val utils = new MarathonAPIUtils(context.system, ActorMaterializer())

  override def receive: Receive = {
    case TriggerCleaning =>
      utils.removeEmptyFoldersFromDCOS
  }
}


object EnvironmentCleanerActor {

  trait Notification

  object TriggerCleaning extends Notification
}