/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.sparta.serving.core.actor

import com.stratio.sparta.serving.core.constants.AppConstant
import com.stratio.sparta.serving.core.models.workflow.Workflow
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type
import org.apache.curator.framework.recipes.cache.{PathChildrenCache, PathChildrenCacheEvent, PathChildrenCacheListener}
import org.json4s.jackson.Serialization.read

import scala.util.Try

class WorkflowPublisherActor(curatorFramework: CuratorFramework) extends ListenerPublisher {

  import WorkflowPublisherActor._

  val relativePath = AppConstant.WorkflowsZkPath

  override def initNodeListener(): Unit = {
    val nodeListener = new PathChildrenCacheListener {
      override def childEvent(client: CuratorFramework, event: PathChildrenCacheEvent): Unit = {
        val eventData = event.getData
        Try {
          read[Workflow](new String(eventData.getData))
        } foreach { workflow =>
          event.getType match {
            case Type.CHILD_ADDED | Type.CHILD_UPDATED =>
                self ! WorkflowChange(event.getData.getPath, workflow)
            case Type.CHILD_REMOVED =>
                self ! WorkflowRemove(event.getData.getPath, workflow)
            case _ => {}
          }
        }
      }
    }

    pathCache = Option(new PathChildrenCache(curatorFramework, relativePath, true))
    pathCache.foreach(_.getListenable.addListener(nodeListener, context.dispatcher))
    pathCache.foreach(_.start())
  }

  override def receive: Receive = workflowReceive.orElse(listenerReceive)

  def workflowReceive: Receive = {
    case cd: WorkflowChange =>
      context.system.eventStream.publish(cd)
    case cd: WorkflowRemove =>
      context.system.eventStream.publish(cd)
  }
}

object WorkflowPublisherActor {

  trait Notification

  case class WorkflowChange(path: String, workflow: Workflow) extends Notification

  case class WorkflowRemove(path: String, workflow: Workflow) extends Notification

}