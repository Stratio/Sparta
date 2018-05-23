/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.serving.core.services

import akka.actor.{ActorRef, ActorSystem}
import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.sdk.models.{DebugResults, ResultStep, WorkflowError}
import com.stratio.sparta.serving.core.constants.AppConstant._
import com.stratio.sparta.serving.core.exception.ServerException
import com.stratio.sparta.serving.core.factory.CuratorFactoryHolder
import com.stratio.sparta.serving.core.models.SpartaSerializer
import com.stratio.sparta.serving.core.models.workflow.DebugWorkflow
import com.stratio.sparta.serving.core.services.WorkflowService._
import org.apache.curator.framework.CuratorFramework
import org.json4s.jackson.Serialization.{read, _}

import scala.collection.JavaConversions
import scala.util.Try

class DebugWorkflowService(
                            curatorFramework: CuratorFramework,
                            override val serializerSystem: Option[ActorSystem] = None,
                            override val environmentStateActor: Option[ActorRef] = None
                          ) extends SpartaSerializer with SLF4JLogging {

  def createDebugWorkflow(debugWorkflow: DebugWorkflow): Try[DebugWorkflow] =
    Try {
      val storedWorkflow = debugWorkflow.workflowOriginal.id match {
        case Some(id) =>
          findByID(id).toOption
            .fold(writeDebugWorkflowInZk(createDebugWorkflowFromOriginal(debugWorkflow))) { existingWk =>
              updateDebugWorkflowInZk(createDebugWorkflowFromOriginal(debugWorkflow), existingWk)
            }
        case _ => writeDebugWorkflowInZk(createDebugWorkflowFromOriginal(debugWorkflow))
      }
      storedWorkflow
    }

  def findByID(id: String): Try[DebugWorkflow] =
    Try {
      val debugWorkflowLocation = s"$DebugWorkflowZkPath/$id"
      if (CuratorFactoryHolder.existsPath(debugWorkflowLocation)) {
        read[DebugWorkflow](new String(curatorFramework.getData.forPath(debugWorkflowLocation)))
      } else throw new ServerException(errorFindById(id))
    }

  def getResultsByID(id: String): Try[DebugResults] =
    findByID(id).flatMap { debugWorkflow =>
      Try {
        debugWorkflow.result match {
          case Some(result) => result.copy(stepResults = getDebugStepData(id), stepErrors = getDebugStepError(id))
          case None => throw new ServerException(errorFindById(id))
        }
      }
    }

  def clearLastError(id: String): Try[Unit] = {
    log.debug(s"Clearing last debug execution error with id $id")
    setError(id, None)
  }

  def setSuccessful(id: String, state: Boolean): Try[DebugWorkflow] = {
    log.debug(s"Setting state to debug execution with id $id")
    Try {
      val debugWorkflowLocation = s"$DebugWorkflowZkPath/$id"
      if (CuratorFactoryHolder.existsPath(debugWorkflowLocation)) {
        val actualDebug = read[DebugWorkflow](new String(curatorFramework.getData.forPath(debugWorkflowLocation)))
        val newResult = actualDebug.result match {
          case Some(result) => result.copy(debugSuccessful = state)
          case None => DebugResults(state)
        }
        val newDebug = actualDebug.copy(result = Option(newResult))
        curatorFramework.setData().forPath(debugWorkflowLocation, write(newDebug).getBytes)
        newDebug
      } else throw new ServerException(errorFindById(id))
    }
  }

  //scalastyle:off
  def setError(id: String, error: Option[WorkflowError]): Try[Unit] = {
    log.debug(s"Setting error to debug execution error with id $id")
    Try {
      val debugWorkflowLocation = s"$DebugWorkflowZkPath/$id"
      if (CuratorFactoryHolder.existsPath(debugWorkflowLocation)) {
        val actualDebug = read[DebugWorkflow](new String(curatorFramework.getData.forPath(debugWorkflowLocation)))
        error match {
          case Some(wError) =>
            if (wError.step.isDefined) {
              val stepErrorId = s"${wError.step.get}-$id"
              val stepErrorLocation = s"$DebugStepErrorZkPath/$stepErrorId"
              if (CuratorFactoryHolder.existsPath(stepErrorLocation))
                curatorFramework.setData().forPath(stepErrorLocation, write(wError).getBytes)
              else curatorFramework.create().creatingParentsIfNeeded().forPath(stepErrorLocation, write(wError).getBytes)
            } else {
              val newDebugResult = actualDebug.result match {
                case Some(result) => result.copy(genericError = error)
                case None => DebugResults(debugSuccessful = true, stepResults = Map.empty, stepErrors = Map.empty, genericError = error)
              }
              val newDebug = actualDebug.copy(result = Option(newDebugResult))
              curatorFramework.setData().forPath(debugWorkflowLocation, write(newDebug).getBytes)
            }
          case None =>
            val newDebug = actualDebug.copy(result = actualDebug.result.map(result => result.copy(genericError = None, stepErrors = Map.empty)))
            curatorFramework.setData().forPath(debugWorkflowLocation, write(newDebug).getBytes)
            val stepErrorKey = curatorFramework.getChildren.forPath(DebugStepErrorZkPath)
            JavaConversions.asScalaBuffer(stepErrorKey).toList.foreach { element =>
              if (element.contains(id))
                curatorFramework.delete().forPath(s"$DebugStepErrorZkPath/$element")
            }
        }
      } else throw new ServerException(errorFindById(id))
    }
  }

  //scalastyle:on

  private def getDebugStepData(id: String): Map[String, ResultStep] =
    Try {
      if (CuratorFactoryHolder.existsPath(DebugStepDataZkPath)) {
        val stepDataKey = curatorFramework.getChildren.forPath(DebugStepDataZkPath)
        JavaConversions.asScalaBuffer(stepDataKey).toList.flatMap { element =>
          if (element.contains(id))
            Try {
              val resultStep = read[ResultStep](
                new String(curatorFramework.getData.forPath(s"$DebugStepErrorZkPath/$element")))
              resultStep.step -> resultStep
            }.toOption
          else None
        }.toMap
      } else Map.empty[String, ResultStep]
    }.getOrElse(Map.empty[String, ResultStep])

  private def getDebugStepError(id: String): Map[String, WorkflowError] =
    Try {
      if (CuratorFactoryHolder.existsPath(DebugStepErrorZkPath)) {
        val stepErrorKey = curatorFramework.getChildren.forPath(DebugStepErrorZkPath)
        JavaConversions.asScalaBuffer(stepErrorKey).toList.flatMap { element =>
          if (element.contains(id))
            Try {
              val workflowError = read[WorkflowError](
                new String(curatorFramework.getData.forPath(s"$DebugStepErrorZkPath/$element")))
              workflowError.step.get -> workflowError
            }.toOption
          else None
        }.toMap
      } else Map.empty[String, WorkflowError]
    }.getOrElse(Map.empty[String, WorkflowError])

  private def createDebugWorkflowFromOriginal(debugWorkflow: DebugWorkflow): DebugWorkflow =
    debugWorkflow.copy(workflowDebug = Option(debugWorkflow.transformToWorkflowRunnable))

  private def writeDebugWorkflowInZk(debugWorkflow: DebugWorkflow): DebugWorkflow = {
    val updatedOriginalWorkflow = debugWorkflow.copy(workflowOriginal =
      addCreationDate(addId(debugWorkflow.workflowOriginal)))
    curatorFramework.create.creatingParentsIfNeeded.forPath(
      s"$DebugWorkflowZkPath/${updatedOriginalWorkflow.workflowOriginal.id.get}",
      write(updatedOriginalWorkflow).getBytes)
    updatedOriginalWorkflow
  }

  private def updateDebugWorkflowInZk(debugWorkflow: DebugWorkflow, oldDebugWorkflow: DebugWorkflow): DebugWorkflow = {
    val updatedWorkflowId = debugWorkflow.copy(workflowOriginal = addUpdateDate(debugWorkflow.workflowOriginal
      .copy(id = oldDebugWorkflow.workflowOriginal.id)))
    curatorFramework.setData().forPath(
      s"$DebugWorkflowZkPath/${debugWorkflow.workflowOriginal.id.get}", write(updatedWorkflowId).getBytes)
    updatedWorkflowId
  }

  private def errorFindById(id: String): String = s"No debug workflow found for workflow id: $id"

}