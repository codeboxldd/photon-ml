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
package com.linkedin.photon.ml.diagnostics.hl

import org.testng.annotations.{DataProvider, Test}

class PredictedProbabilityVersusObservedFrequencyHistogramBinTest {

  import org.testng.Assert._

  @DataProvider
  def generateObservedVersusExpectedCases(): Array[Array[Any]] = {
    Array(
      Array(0.0, 1.0, 1000L, 500L, 500L),
      Array(0.0, 0.5, 1000L, 250L, 750L),
      Array(0.5, 1.0, 1000L, 750L, 250L),
      Array(0.0, 0.1, 1000L, 50L, 950L),
      Array(0.9, 1.0, 1000L, 950L, 50L)
    )
  }

  @Test(dataProvider = "generateObservedVersusExpectedCases")
  def checkObservedVersusExpected(minPred: Double, maxPred: Double, numSamples: Long, expectedPos: Long, expectedNeg: Long): Unit = {
    val bin = new PredictedProbabilityVersusObservedFrequencyHistogramBin(minPred, maxPred, numSamples, 0)
    assertEquals(bin.expectedNegCount, expectedNeg, "Computed value of expected negative count matches")
    assertEquals(bin.expectedPosCount, expectedPos, "Computed value of expected positive count matches")
  }
}
