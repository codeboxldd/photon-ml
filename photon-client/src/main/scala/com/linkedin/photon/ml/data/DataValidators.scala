/*
 * Copyright 2017 LinkedIn Corp. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linkedin.photon.ml.data

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.mllib.linalg.SparseVector

import com.linkedin.photon.ml.DataValidationType.DataValidationType
import com.linkedin.photon.ml.TaskType.TaskType
import com.linkedin.photon.ml.Types.FeatureShardId
import com.linkedin.photon.ml.supervised.classification.BinaryClassifier
import com.linkedin.photon.ml.util.Logging
import com.linkedin.photon.ml.{DataValidationType, TaskType}

import scala.util.{Success, Try}

/**
 * A collection of methods used to validate data before applying ML algorithms.
 */
object DataValidators extends Logging {

  // (Validator, Error Message) pairs
  val baseValidators: List[((LabeledPoint => Boolean), String)] = List(
    (finiteFeatures, "Data contains row(s) with non-finite feature(s)"),
    (finiteOffset, "Data contains row(s) with non-finite offset(s)"),
    (finiteWeight, "Data contains row(s) with non-finite weight(s)"))
  val linearRegressionValidators: List[((LabeledPoint => Boolean), String)] =
    (finiteLabel _, "Data contains row(s) with non-finite label(s)") :: baseValidators
  val logisticRegressionValidators: List[((LabeledPoint => Boolean), String)] =
    (binaryLabel _, "Data contains row(s) with non-binary label(s)") :: baseValidators
  val poissonRegressionValidators: List[((LabeledPoint => Boolean), String)] =
    (finiteLabel _, "Data contains row(s) with non-finite label(s)") ::
      (nonNegativeLabels _, "Data contains row(s) with negative label(s)") ::
      baseValidators

  // (Validator, Input Column Name, Error Message) triples
  val dataFrameBaseValidators: List[(((Row, String) => Boolean), InputColumnsNames.Value, String)] = List(
    (rowHasFiniteFeatures, InputColumnsNames.FEATURES_DEFAULT, "Data contains row(s) with non-finite feature(s)"),
    (rowHasFiniteWeight, InputColumnsNames.WEIGHT, "Data contains row(s) with non-finite weight(s)"),
    (rowHasFiniteOffset, InputColumnsNames.OFFSET, "Data contains row(s) with non-finite offset(s)"))
  val dataFrameLinearRegressionValidators: List[(((Row, String) => Boolean), InputColumnsNames.Value, String)] =
    (rowHasFiniteLabel _, InputColumnsNames.RESPONSE, "Data contains row(s) with non-finite label(s)") :: dataFrameBaseValidators
  val dataFrameLogisticRegressionValidators: List[(((Row, String) => Boolean), InputColumnsNames.Value, String)] =
    (rowHasBinaryLabel _, InputColumnsNames.RESPONSE, "Data contains row(s) with non-binary label(s)") :: dataFrameBaseValidators
  val dataFramePoissonRegressionValidators: List[(((Row, String) => Boolean), InputColumnsNames.Value, String)] =
    (rowHasFiniteLabel _, InputColumnsNames.RESPONSE, "Data contains row(s) with non-finite label(s)") ::
      (rowHasNonNegativeLabels _, InputColumnsNames.RESPONSE, "Data contains row(s) with negative label(s)") ::
      dataFrameBaseValidators

  /**
   * Verify that a labeled data point has a finite label.
   *
   * @param labeledPoint The input data point
   * @return Whether the label of the input data point is finite
   */
  def finiteLabel(labeledPoint: LabeledPoint): Boolean = !(labeledPoint.label.isNaN || labeledPoint.label.isInfinite)

  /**
   * Verify that a labeled data point has a finite label.
   *
   * @param row The input row from a data frame
   * @param inputColumnName The column name we want to validate
   * @return Whether the label of the input data point is finite
   */
  def rowHasFiniteLabel(row: Row, inputColumnName: String): Boolean = {
    val label = row.getAs[Double](inputColumnName)
    !(label.isNaN || label.isInfinite)
  }

  /**
   * Verify that a labeled data point has a binary label.
   *
   * @param labeledPoint The input data point
   * @return Whether the label of the input data point is binary
   */
  def binaryLabel(labeledPoint: LabeledPoint): Boolean =
    BinaryClassifier.positiveClassLabel == labeledPoint.label || BinaryClassifier.negativeClassLabel == labeledPoint.label

  /**
   * Verify that a labeled data point has a binary label.
   *
   * @param row The input row from a data frame
   * @param inputColumnName The column name we want to validate
   * @return Whether the label of the input data point is binary
   */
  def rowHasBinaryLabel(row: Row, inputColumnName: String): Boolean = {
    val label = row.getAs[Double](inputColumnName)
    BinaryClassifier.positiveClassLabel == label || BinaryClassifier.negativeClassLabel == label
  }

  /**
   * Verify that a labeled data point has a non-negative label.
   *
   * @param labeledPoint The input data point
   * @return Whether the label of the input data point is non-negative
   */
  def nonNegativeLabels(labeledPoint: LabeledPoint): Boolean = labeledPoint.label >= 0

  /**
   * Verify that a row has a non-negative label.
   *
   * @param row The input row from a data frame
   * @param inputColumnName The column name we want to validate
   * @return Whether the label of the input data point is non-negative
   */
  def rowHasNonNegativeLabels(row: Row, inputColumnName: String): Boolean = {
    val label = row.getAs[Double](inputColumnName)
    label >= 0
  }

  /**
   * Verify that the feature values of a data point are finite.
   *
   * @param labeledPoint The input data point
   * @return Whether all feature values for the input data point are finite
   */
  def finiteFeatures(labeledPoint: LabeledPoint): Boolean =
    labeledPoint.features.iterator.forall { case (_, value) =>
      !(value.isNaN || value.isInfinite)
    }

  /**
   * Verify that the feature values of a data point are finite.
   *
   * @param row The input row from a data frame
   * @param inputColumnName The column name we want to validate
   * @return Whether all feature values for the input data point are finite
   */
  def rowHasFiniteFeatures(row: Row, inputColumnName: String): Boolean = {
    val features = row.getAs[SparseVector](inputColumnName).values
    features.iterator.forall { case (value) =>
      !(value.isNaN || value.isInfinite)
    }
  }

  /**
   * Verify that a data point has a finite offset.
   *
   * @param labeledPoint The input data point
   * @return Whether the offset of the input data point is finite
   */
  def finiteOffset(labeledPoint: LabeledPoint): Boolean = !(labeledPoint.offset.isNaN || labeledPoint.offset.isInfinite)

  /**
   * Verify that a row has a finite offset.
   *
   * @param row The input row from a data frame
   * @param inputColumnName The column name we want to validate
   * @return Whether the offset of the input data point is finite
   */
  def rowHasFiniteOffset(row: Row, inputColumnName: String): Boolean = {
    val offset = row.getAs[Double](inputColumnName)
    !(offset.isNaN || offset.isInfinite)
  }

  /**
   * Verify that a data point has a finite weight.
   *
   * @param labeledPoint The input data point
   * @return Whether the weight of the input data point is finite
   */
  def finiteWeight(labeledPoint: LabeledPoint): Boolean = !(labeledPoint.weight.isNaN || labeledPoint.weight.isInfinite)

  /**
   * Verify that a row has a finite weight.
   *
   * @param row The input row from a data frame
   * @param inputColumnName The column name we want to validate
   * @return Whether the weight of the input data point is finite
   */
  def rowHasFiniteWeight(row: Row, inputColumnName: String): Boolean = {
    val weight = row.getAs[Double](inputColumnName)
    !(weight.isNaN || weight.isInfinite)
  }

  /**
   * Validate a data set using one or more data point validators.
   *
   * @param dataSet The input data set
   * @param perSampleValidators A list of (data validator, error message) pairs
   * @return The list of validation error messages for the input data
   */
  private def validateData(
      dataSet: RDD[LabeledPoint],
      perSampleValidators: List[((LabeledPoint => Boolean), String)]): Seq[String] =
    perSampleValidators
      .map { case (validator, msg) =>
        val validatorBroadcast = dataSet.sparkContext.broadcast(validator)
        val result = dataSet.aggregate(true)(
          seqOp = (result, dataPoint) => result && validatorBroadcast.value(dataPoint),
          combOp = (result1, result2) => result1 && result2)

        (result, msg)
      }
      .filterNot(_._1)
      .map(_._2)

  /**
   * Validate a full or sampled data set using the set of data point validators relevant to the training problem.
   *
   * @param inputData The input data set
   * @param taskType The training task type
   * @param dataValidationType The validation intensity
   * @throws IllegalArgumentException if one or more of the data validations failed
   */
  def sanityCheckData(
      inputData: RDD[LabeledPoint],
      taskType: TaskType,
      dataValidationType: DataValidationType): Unit = {

    val validators: List[((LabeledPoint => Boolean), String)] = taskType match {
      case TaskType.LINEAR_REGRESSION => linearRegressionValidators
      case TaskType.LOGISTIC_REGRESSION => logisticRegressionValidators
      case TaskType.POISSON_REGRESSION => poissonRegressionValidators
      case TaskType.SMOOTHED_HINGE_LOSS_LINEAR_SVM => logisticRegressionValidators
    }

    // Check the data properties
    val dataErrors = dataValidationType match {
      case DataValidationType.VALIDATE_FULL =>
        validateData(inputData, validators)

      case DataValidationType.VALIDATE_SAMPLE =>
        validateData(inputData.sample(withReplacement = false, fraction = 0.10), validators)

      case DataValidationType.VALIDATE_DISABLED =>
        Seq()
    }

    if (dataErrors.nonEmpty) {
      throw new IllegalArgumentException(s"Data Validation failed:\n${dataErrors.mkString("\n")}")
    }
  }

  /**
   * Validate a data frame using one or more data point validators.
   *
   * @param dataSet The input data frame
   * @param perSampleValidators A list of (data validator, input column name, error message) triples
   * @param inputColumnsNames Column names for the provided data frame
   * @param featureSectionKeys Column names for the feature columns in the provided data frame
   * @return The list of validation error messages for the input data frame
   */
  private def validateDataFrame(
      dataSet: DataFrame,
      perSampleValidators: List[(((Row, String) => Boolean), InputColumnsNames.Value, String)],
      inputColumnsNames: InputColumnsNames,
      featureSectionKeys: Set[FeatureShardId]): Seq[String] = {

    val columns = dataSet.columns
    dataSet.rdd.flatMap { r =>
      perSampleValidators.flatMap { case (validator, columnName, msg) =>
        if (columnName == InputColumnsNames.FEATURES_DEFAULT) {
          featureSectionKeys.map(features => (validator(r, features), msg))
        } else {
          val result = if (columns.contains(inputColumnsNames(columnName))) {
            (validator(r, inputColumnsNames(columnName)), msg)
          } else {
            (true, "")
          }

          Seq(result)
        }
      }
      .filterNot(_._1)
      .map(_._2)
    }
    .collect()
  }

  /**
   * Validate a full or sampled data frame using the set of data point validators relevant to the training problem.
   *
   * @param inputData The input data frame
   * @param taskType The training task type
   * @param dataValidationType The validation intensity
   * @param inputColumnsNames Column names for the provided data frame
   * @param featureSectionKeys Column names for the feature columns in the provided data frame
   * @throws IllegalArgumentException if one or more of the data validations failed
   */
  def sanityCheckDataFrame(
      inputData: DataFrame,
      taskType: TaskType,
      dataValidationType: DataValidationType,
      inputColumnsNames: InputColumnsNames,
      featureSectionKeys: Set[FeatureShardId]): Unit = {

    val validators: List[(((Row, String) => Boolean), InputColumnsNames.Value, String)] = taskType match {
      case TaskType.LINEAR_REGRESSION => dataFrameLinearRegressionValidators
      case TaskType.LOGISTIC_REGRESSION => dataFrameLogisticRegressionValidators
      case TaskType.POISSON_REGRESSION => dataFramePoissonRegressionValidators
      case TaskType.SMOOTHED_HINGE_LOSS_LINEAR_SVM => dataFrameLogisticRegressionValidators
    }

    // Check the data properties
    val dataErrors = dataValidationType match {
      case DataValidationType.VALIDATE_FULL =>
        validateDataFrame(
          inputData,
          validators,
          inputColumnsNames,
          featureSectionKeys)

      case DataValidationType.VALIDATE_SAMPLE =>
        validateDataFrame(
          inputData.sample(withReplacement = false, fraction = 0.10),
          validators,
          inputColumnsNames,
          featureSectionKeys)

      case DataValidationType.VALIDATE_DISABLED =>
        Seq()
    }

    if (dataErrors.nonEmpty) {
      throw new IllegalArgumentException(s"Data Validation failed:\n${dataErrors.mkString("\n")}")
    }
  }
}
