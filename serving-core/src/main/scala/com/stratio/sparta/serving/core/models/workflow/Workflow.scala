/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.serving.core.models.workflow

import com.stratio.sparta.serving.core.constants.AppConstant.DefaultGroup
import com.stratio.sparta.serving.core.models.enumerators.WorkflowExecutionEngine
import com.stratio.sparta.serving.core.models.enumerators.WorkflowExecutionEngine._
import org.joda.time.DateTime

import com.stratio.sparta.serving.core.models.dto.Dto

case class Workflow(
                     id: Option[String] = None,
                     name: String,
                     description: String = "Default description",
                     settings: Settings,
                     pipelineGraph: PipelineGraph,
                     executionEngine : ExecutionEngine = WorkflowExecutionEngine.Streaming,
                     uiSettings: Option[UiSettings] = None,
                     creationDate: Option[DateTime] = None,
                     lastUpdateDate: Option[DateTime] = None,
                     version: Long = 0L,
                     group: Group = DefaultGroup,
                     tags: Option[Seq[String]] = None,
                     status: Option[WorkflowStatus] = None,
                     execution: Option[WorkflowExecution] = None,
                     debugMode: Option[Boolean] = Option(false)
                   )

/**
  * Wrapper class used by the api consumers
  */
case class WorkflowDto(id: Option[String],
                       name: String,
                       description: String,
                       settings: GlobalSettings,
                       nodes: Seq[NodeGraphDto],
                       executionEngine: ExecutionEngine,
                       lastUpdateDate: Option[DateTime],
                       version: Long,
                       group: String,
                       tags: Option[Seq[String]] = None,
                       status: Option[WorkflowStatus],
                       execution: Option[WorkflowExecutionDto]) extends Dto