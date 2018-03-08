/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.serving.core.actor

import akka.actor.Actor
import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.serving.core.constants.AppConstant
import com.stratio.sparta.serving.core.models.SpartaSerializer
import com.stratio.sparta.serving.core.models.workflow.Group
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type
import org.apache.curator.framework.recipes.cache.{PathChildrenCache, PathChildrenCacheEvent, PathChildrenCacheListener}
import org.json4s.jackson.Serialization.read

import scala.util.Try

class GroupPublisherActor(curatorFramework: CuratorFramework) extends Actor with SpartaSerializer with SLF4JLogging {

  import GroupPublisherActor._

  private var pathCache: Option[PathChildrenCache] = None

  override def preStart(): Unit = {
    val groupZkPath = AppConstant.GroupZkPath
    val nodeListener = new PathChildrenCacheListener {
      override def childEvent(client: CuratorFramework, event: PathChildrenCacheEvent): Unit = {
        val eventData = event.getData
        Try {
          read[Group](new String(eventData.getData))
        } foreach { group =>
          event.getType match {
            case Type.CHILD_ADDED | Type.CHILD_UPDATED =>
              self ! GroupChange(event.getData.getPath, group)
            case Type.CHILD_REMOVED =>
              self ! GroupRemove(event.getData.getPath, group)
            case _ => {}
          }
        }
      }
    }

    pathCache = Option(new PathChildrenCache(curatorFramework, groupZkPath, true))
    pathCache.foreach(_.getListenable.addListener(nodeListener, context.dispatcher))
    pathCache.foreach(_.start())
  }

  override def postStop(): Unit =
    pathCache.foreach(_.close())

  override def receive: Receive = {
    case cd: GroupChange =>
      context.system.eventStream.publish(cd)
    case cd: GroupRemove =>
      context.system.eventStream.publish(cd)
    case _ =>
      log.debug("Unrecognized message in Group Publisher Actor")
  }

}

object GroupPublisherActor {

  trait Notification

  case class GroupChange(path: String, group: Group) extends Notification

  case class GroupRemove(path: String, group: Group) extends Notification

}