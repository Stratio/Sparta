/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.sparta.serving.api.actor

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, AllForOneStrategy, Props, SupervisorStrategy}
import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.dg.agent.lineage.LineageService
import com.stratio.sparta.serving.core.actor.{ExecutionStatusChangeListenerActor, ExecutionStatusChangePublisherActor, SchedulerMonitorActor}
import com.stratio.sparta.serving.core.config.SpartaConfig
import com.stratio.sparta.serving.core.constants.MarathonConstant.NginxMarathonLBHostEnv

import scala.util.{Properties, Try}

class TopLevelSupervisorActor extends Actor with SLF4JLogging {

  implicit val ec = context.system.dispatchers.lookup("sparta-actors-dispatcher")

  override def receive: Receive = {
    case _ => log.debug("Unsupported message received in TopLevelSupervisorActor")
  }

  override def supervisorStrategy: SupervisorStrategy = AllForOneStrategy(){
    case _ => Restart
  }

  override def postStop(): Unit = {
    log.warn(s"Stopped TopLevelSupervisorActor at time ${System.currentTimeMillis()}")
  }

  override def preStart(): Unit = {

    context.actorOf(Props(new ExecutionStatusChangePublisherActor()))

    context.actorOf(Props[SchedulerMonitorActor])

    //Initialize Nginx actor
    if (Properties.envOrNone(NginxMarathonLBHostEnv).fold(false) { _ => true }) {
      log.info("Initializing Nginx service")
      Option(context.actorOf(Props(new NginxActor())))
    }

    if (Try(SpartaConfig.getDetailConfig().get.getBoolean("lineage.enable")).getOrElse(false)) {
      val executionStatusChangeListenerActor = context.actorOf(Props(new ExecutionStatusChangeListenerActor()))
      log.info("Initializing lineage service")
      context.actorOf(LineageService.props(executionStatusChangeListenerActor))
    }

  }

}