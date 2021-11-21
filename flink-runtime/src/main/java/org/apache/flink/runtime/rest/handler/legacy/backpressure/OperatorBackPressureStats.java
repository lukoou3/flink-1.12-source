/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.rest.handler.legacy.backpressure;

import java.io.Serializable;
import java.util.Arrays;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Back pressure statistics of multiple tasks generated by {@link BackPressureStatsTrackerImpl}. */
public class OperatorBackPressureStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID of the corresponding request. */
    private final int requestId;

    /**
     * End time stamp when all responses of the back pressure request were collected at
     * BackPressureRequestCoordinator.
     */
    private final long endTimestamp;

    /** Back pressure ratio per subtask. */
    private final double[] subTaskBackPressureRatios;

    /** Maximum back pressure ratio. */
    private final double maxSubTaskBackPressureRatio;

    public OperatorBackPressureStats(
            int requestId, long endTimestamp, double[] subTaskBackPressureRatios) {

        this.requestId = requestId;
        this.endTimestamp = endTimestamp;
        this.subTaskBackPressureRatios = checkNotNull(subTaskBackPressureRatios);
        checkArgument(
                subTaskBackPressureRatios.length >= 1,
                "No Sub task back pressure ratio specified.");

        double max = 0;
        for (double ratio : subTaskBackPressureRatios) {
            if (ratio > max) {
                max = ratio;
            }
        }

        maxSubTaskBackPressureRatio = max;
    }

    public int getRequestId() {
        return requestId;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public int getNumberOfSubTasks() {
        return subTaskBackPressureRatios.length;
    }

    /** Returns the back pressure ratio of the given subtask index. */
    public double getBackPressureRatio(int index) {
        return subTaskBackPressureRatios[index];
    }

    /** Returns the maximum back pressure ratio of all sub tasks. */
    public double getMaxBackPressureRatio() {
        return maxSubTaskBackPressureRatio;
    }

    @Override
    public String toString() {
        return "OperatorBackPressureStats{"
                + "requestId="
                + requestId
                + ", endTimestamp="
                + endTimestamp
                + ", subTaskBackPressureRatios="
                + Arrays.toString(subTaskBackPressureRatios)
                + '}';
    }
}
