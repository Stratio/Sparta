/**
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparkta.sdk.test

import java.io.{Serializable => JSerializable}

import com.stratio.sparkta.sdk.{Operator, TypeOp, WriteOp}

class OperatorTest(name: String, properties: Map[String, JSerializable]) extends Operator(name, properties) {

  override val defaultTypeOperation = TypeOp.Long

  override val writeOperation = WriteOp.Inc

  override val castingFilterType = TypeOp.Number

  override def processMap(inputFields: Map[String, JSerializable]): Option[Any] = {
    None
  }

  override def processReduce(values: Iterable[Option[Any]]): Option[Long] = {
    None
  }
}

class OperatorTestString(name: String, properties: Map[String, JSerializable]) extends Operator(name, properties) {

  override val defaultTypeOperation = TypeOp.Long

  override val writeOperation = WriteOp.Inc

  override val castingFilterType = TypeOp.String

  override def processMap(inputFields: Map[String, JSerializable]): Option[Any] = {
    None
  }

  override def processReduce(values: Iterable[Option[Any]]): Option[Long] = {
    None
  }
}
