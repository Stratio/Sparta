/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.security

case class Resource(resourceType: ResourceType, name: String)

sealed trait ResourceType{
  def name(): String
}

case object CatalogResource extends ResourceType{
  override def name(): String = "Catalog"
}

case object ConfigurationResource extends ResourceType{
  override def name(): String = "Configuration"
}

case object DashboardResource extends ResourceType{
  override def name(): String = "Dashboard"
}

case object FilesResource extends ResourceType{
  override def name(): String = "Files"
}

case object GlobalParametersResource extends ResourceType{
  override def name(): String = "GlobalParameters"
}

case object GroupsResource extends ResourceType{
  override def name(): String = "Groups"
}

case object MlModelsResource extends ResourceType{
  override def name(): String = "MlModels"
}

case object ParameterListResource extends ResourceType{
  override def name(): String = "ParameterList"
}

case object TemplateResource extends ResourceType{
  override def name(): String = "Template"
}

case object WorkflowsResource extends ResourceType{
  override def name(): String = "Workflows"
}

case object QRResultResource extends ResourceType{
  override def name(): String = "QRResult"
}