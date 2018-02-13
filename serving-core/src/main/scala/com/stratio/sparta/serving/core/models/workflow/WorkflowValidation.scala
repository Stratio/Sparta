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

import com.stratio.sparta.sdk.workflow.step.OutputStep
import com.stratio.sparta.serving.core.constants.AppConstant
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.serving.core.helpers.WorkflowHelper
import com.stratio.sparta.serving.core.models.enumerators.ArityValueEnum.{ArityValue, _}
import com.stratio.sparta.serving.core.models.enumerators.NodeArityEnum.{NodeArity, _}
import com.stratio.sparta.serving.core.services.GroupService
import org.apache.curator.framework.CuratorFramework

import scala.util.Try
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphTraversal.Visitor

case class WorkflowValidation(valid: Boolean, messages: Seq[String]) {

  def this(valid: Boolean) = this(valid, messages = Seq.empty[String])

  def this() = this(valid = true)

  val InputMessage = "input"
  val OutputMessage = "output"

  /** A group name is correct if and only if:
    * 1) there are no two or more consecutives /
    * 2) it starts with /home (the default root)
    * 3) the group name has no Uppercase letters or other special characters (except / and -)
    */
  val regexGroups = "^(?!.*[/]{2}.*$)(^(/home)+(/)*([a-z0-9-/]*)$)"

  // A Workflow name should not contain special characters and Uppercase letters (because of DCOS deployment)
  val regexName = "^[a-z0-9-]*"

  def validateExecutionMode(implicit workflow: Workflow): WorkflowValidation = {
    if ((workflow.settings.global.executionMode == AppConstant.ConfigMarathon ||
      workflow.settings.global.executionMode == AppConstant.ConfigMesos) &&
      !workflow.settings.sparkSettings.master.toString.startsWith("mesos://"))
      copy(
        valid = false,
        messages = messages :+ s"The selected execution mode is Marathon or Mesos," +
          s" therefore Spark Master should start with mesos://"
      )
    else if (workflow.settings.global.executionMode == AppConstant.ConfigLocal &&
      !workflow.settings.sparkSettings.master.toString.startsWith("local"))
      copy(
        valid = false,
        messages = messages :+ s"The selected execution mode is local, therefore, the Spark Master " +
          s"value should start with local"
      )
    else this
  }

  def validateDeployMode(implicit workflow: Workflow): WorkflowValidation = {
    if (workflow.settings.global.executionMode == AppConstant.ConfigMarathon &&
      workflow.settings.sparkSettings.submitArguments.deployMode.notBlank.isDefined &&
      workflow.settings.sparkSettings.submitArguments.deployMode.get != "client")
      copy(
        valid = false,
        messages = messages :+ s"The selected execution mode is Marathon and the deploy mode is not client"
      )
    else if (workflow.settings.global.executionMode == AppConstant.ConfigMesos &&
      workflow.settings.sparkSettings.submitArguments.deployMode.notBlank.isDefined &&
      workflow.settings.sparkSettings.submitArguments.deployMode.get != "cluster")
      copy(
        valid = false,
        messages = messages :+ s"The selected execution mode is Mesos and the deploy mode is not cluster"
      )
    else this
  }

  def validateSparkCores(implicit workflow: Workflow): WorkflowValidation = {
    if ((workflow.settings.global.executionMode == AppConstant.ConfigMarathon ||
      workflow.settings.global.executionMode == AppConstant.ConfigMesos) &&
      workflow.settings.sparkSettings.sparkConf.sparkResourcesConf.coresMax.notBlank.isDefined &&
        workflow.settings.sparkSettings.sparkConf.sparkResourcesConf.executorCores.notBlank.isDefined &&
      workflow.settings.sparkSettings.sparkConf.sparkResourcesConf.coresMax.get.toString.toDouble <
        workflow.settings.sparkSettings.sparkConf.sparkResourcesConf.executorCores.get.toString.toDouble)
      copy(
        valid = false,
        messages = messages :+ s"The total number of executor cores (max cores) should be greater than" +
          s" the number of executor cores"
      )
    else this
  }

  def validateCheckpointCubes(implicit workflow: Workflow): WorkflowValidation =
    workflow.pipelineGraph.nodes.find(node => node.className == "CubeTransformStep") match {
      case Some(_) => if (!workflow.settings.streamingSettings.checkpointSettings.enableCheckpointing)
        copy(
          valid = false,
          messages = messages :+ s"The workflow contains Cubes and the checkpoint is not enabled"
        )
      else this
      case None => this
    }

  def validateGroupName(implicit workflow: Workflow, curator: Option[CuratorFramework]): WorkflowValidation = {
    if (curator.isEmpty) this
    else {
      val groupService = new GroupService(curator.get)
      val groupInZk = groupService.findByID(workflow.group.id.get).toOption
      if (groupInZk.isDefined && workflow.group.name.equals(groupInZk.get.name)
        && workflow.group.name.matches(regexGroups))
        this
      else {
        val msg = messages :+ "The workflow group does not exist or is invalid"
        copy(valid = false, messages = msg)
      }
    }
  }

  def validateMesosConstraints(implicit workflow: Workflow): WorkflowValidation = {
    if (workflow.settings.global.mesosConstraint.notBlank.isDefined && !workflow.settings.global.
      mesosConstraint.get.toString.contains(":"))
      copy(
        valid = false,
        messages = messages :+ s"The Mesos constraints must be two alphanumeric strings separated by a colon"
      )
    else if(workflow.settings.global.mesosConstraint.notBlank.isDefined &&
      workflow.settings.global.mesosConstraint.get.toString.contains(":") &&
      workflow.settings.global.mesosConstraint.get.toString.count(_ == ':') > 1)
      copy(
        valid = false,
        messages = messages :+ s"The colon may appear only once in the Mesos constraint definition"
      )
    else if(workflow.settings.global.mesosConstraint.notBlank.isDefined &&
      workflow.settings.global.mesosConstraint.get.toString.contains(":") &&
      (workflow.settings.global.mesosConstraint.get.toString.indexOf(":") == 0 ||
        workflow.settings.global.mesosConstraint.get.toString.indexOf(":") ==
          workflow.settings.global.mesosConstraint.get.toString.length))
      copy(
        valid = false,
        messages = messages :+ s"The colon cannot be situated at the edges of the Mesos constraint definition"
      )
    else this
  }

  def validateErrorOutputs(implicit workflow: Workflow): WorkflowValidation = {
    val errorOutputNodes = workflow.pipelineGraph.nodes.filter { node =>
      val isSinkOutput = Try(node.configuration(WorkflowHelper.OutputStepErrorProperty).toString.toBoolean)
        .getOrElse(false)
      node.stepType.toLowerCase == OutputStep.StepType && isSinkOutput
    }.map(_.name)

    val saveErrors = workflow.settings.errorsManagement.transactionsManagement.sendToOutputs.flatMap { action =>
      if (!errorOutputNodes.contains(action.outputStepName)) {
        Option(action.outputStepName)
      } else None
    }

    if (saveErrors.nonEmpty) {
      copy(
        valid = false,
        messages = messages :+ s"The workflow has 'Error outputs' defined" +
          s"that don't exist as nodes. ${saveErrors.mkString(", ")}"
      )
    } else this
  }

  def validateName(implicit workflow: Workflow): WorkflowValidation = {
    if (workflow.name.nonEmpty && workflow.name.matches(regexName)) this
    else copy(valid = false, messages = messages :+ "The workflow name is empty or invalid")
  }

  def validateNonEmptyNodes(implicit workflow: Workflow): WorkflowValidation =
    if (workflow.pipelineGraph.nodes.size >= 2) this
    else copy(valid = false, messages = messages :+ "The workflow must contain at least two nodes")

  def validateNonEmptyEdges(implicit workflow: Workflow): WorkflowValidation =
    if (workflow.pipelineGraph.edges.nonEmpty) this
    else copy(valid = false, messages = messages :+ "The workflow must contain at least one relation")

  def validateEdgesNodesExists(implicit workflow: Workflow): WorkflowValidation = {
    val nodesNames = workflow.pipelineGraph.nodes.map(_.name)
    val wrongEdges = workflow.pipelineGraph.edges.flatMap(edge =>
      if (nodesNames.contains(edge.origin) && nodesNames.contains(edge.destination)) None
      else Option(edge)
    )

    if (wrongEdges.isEmpty || workflow.pipelineGraph.edges.isEmpty) this
    else copy(
      valid = false,
      messages = messages :+ s"The workflow has relations that don't exist as nodes: ${wrongEdges.mkString(" , ")}"
    )
  }

  def validateGraphIsAcyclic(implicit workflow: Workflow, graph: Graph[NodeGraph, DiEdge]): WorkflowValidation = {
    val cycle = graph.findCycle

    if (cycle.isEmpty || workflow.pipelineGraph.edges.isEmpty) this
    else copy(
      valid = false,
      messages = messages :+ s"The workflow contains one or more cycles" + {
        if (cycle.isDefined)
          s"${": " + cycle.get.nodes.toList.map(node => node.value.asInstanceOf[NodeGraph].name).mkString(",")}"
        else "!"
      }
    )
  }

  def validateExistenceCorrectPath(implicit workflow: Workflow, graph: Graph[NodeGraph, DiEdge]): WorkflowValidation = {

    def node(outer: NodeGraph): graph.NodeT = (graph get outer).asInstanceOf[graph.NodeT]

    val inputNodes: Seq[graph.NodeT] = workflow.pipelineGraph.nodes
      .filter(node => node.stepType.equals("Input")).map(node(_))
    val outputNodes: Seq[graph.NodeT] = workflow.pipelineGraph.nodes
      .filter(node => node.stepType.equals("Output")).map(node(_))

    val path = {
      for {in <- inputNodes.toStream
           out <- outputNodes.toStream
      } yield {
        in.pathTo(out)(Visitor.empty)
      }
    } exists (_.isDefined)


    if (path) this else this.copy(
      valid = false,
      messages = messages :+ s"The workflow has no I->O path"
    )
  }

  def validateDuplicateNames(implicit workflow: Workflow, graph: Graph[NodeGraph, DiEdge]): WorkflowValidation = {
    val nodes: Seq[NodeGraph] = workflow.pipelineGraph.nodes
    nodes.groupBy(_.name).find(listName => listName._2.size > 1) match {
      case Some(duplicate) =>
        this.copy(
          valid = false,
          messages = messages :+ s"The workflow has two nodes with the same name: ${duplicate._1}"
        )
      case None => this
    }
  }


  def validateArityOfNodes(implicit workflow: Workflow, graph: Graph[NodeGraph, DiEdge]): WorkflowValidation = {
    workflow.pipelineGraph.nodes.foldLeft(this) { case (lastValidation, node) =>
      val nodeInGraph = graph.get(node)
      val inDegree = nodeInGraph.inDegree
      val outDegree = nodeInGraph.outDegree
      val validation = {
        if (node.arity.nonEmpty)
          node.arity.foldLeft(new WorkflowValidation(valid = false)) { case (lastArityValidation, arity) =>
            combineWithOr(lastArityValidation, validateArityDegrees(node, inDegree, outDegree, arity))
          }
        else new WorkflowValidation()
      }

      combineWithAnd(lastValidation, validation)
    }
  }

  def combineWithAnd(first: WorkflowValidation, second: WorkflowValidation): WorkflowValidation =
    if (first.valid && second.valid) new WorkflowValidation()
    else WorkflowValidation(valid = false, messages = first.messages ++ second.messages)

  def combineWithOr(first: WorkflowValidation, second: WorkflowValidation): WorkflowValidation =
    if (first.valid || second.valid) new WorkflowValidation()
    else WorkflowValidation(valid = false, messages = first.messages ++ second.messages)

  private[workflow] def validateArityDegrees(
                                              nodeGraph: NodeGraph,
                                              inDegree: Int,
                                              outDegree: Int,
                                              arity: NodeArity
                                            ): WorkflowValidation =
    arity match {
      case NullaryToNary =>
        combineWithAnd(
          validateDegree(inDegree, Nullary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Nary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
      case UnaryToUnary =>
        combineWithAnd(
          validateDegree(inDegree, Unary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Unary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
      case UnaryToNary =>
        combineWithAnd(
          validateDegree(inDegree, Unary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Nary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
      case BinaryToNary =>
        combineWithAnd(
          validateDegree(inDegree, Binary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Nary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
      case NaryToNullary =>
        combineWithAnd(
          validateDegree(inDegree, Nary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Nullary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
      case NaryToNary =>
        combineWithAnd(
          validateDegree(inDegree, Nary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Nary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
      case NullaryToNullary =>
        combineWithAnd(
          validateDegree(inDegree, Nullary, InvalidMessage(nodeGraph.name, InputMessage)),
          validateDegree(outDegree, Nullary, InvalidMessage(nodeGraph.name, OutputMessage))
        )
    }

  private[workflow] def validateDegree(
                                        degree: Int,
                                        arityDegree: ArityValue,
                                        invalidMessage: InvalidMessage
                                      ): WorkflowValidation =
    arityDegree match {
      case Nullary =>
        validateDegreeValue(degree, 0, invalidMessage)
      case Unary =>
        validateDegreeValue(degree, 1, invalidMessage)
      case Binary =>
        validateDegreeValue(degree, 2, invalidMessage)
      case Nary =>
        if (degree > 0) new WorkflowValidation()
        else WorkflowValidation(
          valid = false,
          messages = Seq(s"Invalid number of relations, the node ${invalidMessage.nodeName} has $degree" +
            s" ${invalidMessage.relationType} relations and support 1 to N")
        )
    }

  private[workflow] def validateDegreeValue(
                                             degree: Int,
                                             arityDegree: Int,
                                             invalidMessage: InvalidMessage
                                           ): WorkflowValidation =
    if (degree == arityDegree) new WorkflowValidation()
    else WorkflowValidation(
      valid = false,
      messages = Seq(s"Invalid number of relations, the node ${invalidMessage.nodeName} has $degree" +
        s" ${invalidMessage.relationType} relations and support $arityDegree")
    )

  case class InvalidMessage(nodeName: String, relationType: String)

}