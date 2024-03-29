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

package org.apache.flink.table.runtime.operators.sort;

import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.table.runtime.util.MemorySegmentPool;

import java.util.List;

/** MemorySegment pool of a MemorySegment list. */
public class ListMemorySegmentPool implements MemorySegmentPool {

    private final List<MemorySegment> segments;
    private final int pageSize;

    public ListMemorySegmentPool(List<MemorySegment> memorySegments) {
        this.segments = memorySegments;
        this.pageSize = segments.get(0).size();
    }

    /**
     * 申请
     * @return
     */
    @Override
    public MemorySegment nextSegment() {
        if (this.segments.size() > 0) {
            return this.segments.remove(this.segments.size() - 1);
        } else {
            return null;
        }
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    /**
     * 归还
     * @param memory the pages which want to be returned.
     */
    @Override
    public void returnAll(List<MemorySegment> memory) {
        segments.addAll(memory);
    }

    @Override
    public int freePages() {
        return segments.size();
    }

    public void clear() {
        segments.clear();
    }
}
