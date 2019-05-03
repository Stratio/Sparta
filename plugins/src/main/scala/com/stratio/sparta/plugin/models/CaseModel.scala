/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.sparta.plugin.models

import com.stratio.sparta.core.models.PropertyField
import com.stratio.sparta.plugin.enumerations.CaseValueType.CaseValueType

import com.stratio.sparta.plugin.enumerations.{CaseOutputStrategy, CaseValueType, SelectType}

case class CaseModel (caseExpression: Option[String] = None,
                      valueType: CaseValueType,
                      value: Option[String] = None,
                      column: Option[String] = None
                     )