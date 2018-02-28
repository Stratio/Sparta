/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparta.serving.core.models.workflow

import com.stratio.sparta.sdk.properties.JsoneyString
import com.stratio.sparta.serving.core.models.enumerators.NodeArityEnum.NodeArity
import com.stratio.sparta.serving.core.models.dto.Dto
import com.stratio.sparta.serving.core.models.enumerators.WorkflowExecutionEngine._

case class NodeGraph(
                      name: String,
                      stepType: String,
                      className: String,
                      classPrettyName: String,
                      arity: Seq[NodeArity],
                      writer: WriterGraph,
                      description: Option[String] = None,
                      createdFromTemplateId: Option[String] = None,
                      uiConfiguration: Option[NodeUiConfiguration] = None,
                      configuration: Map[String, JsoneyString] = Map(),
                      supportedEngines: Seq[ExecutionEngine] = Seq.empty[ExecutionEngine],
                      executionEngine: Option[ExecutionEngine] = Option(Streaming)
                    )

/**
  * Wrapper class used by the api consumers
  */
case class NodeGraphDto(name: String, stepType: String) extends Dto
