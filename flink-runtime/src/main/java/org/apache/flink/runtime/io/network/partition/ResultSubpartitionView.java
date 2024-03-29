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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.ResultSubpartition.BufferAndBacklog;

import javax.annotation.Nullable;

import java.io.IOException;

/** A view to consume a {@link ResultSubpartition} instance. */
public interface ResultSubpartitionView {

    /**
     * 返回此队列迭代器的下一个Buffer实例。
     *
     * 如果当前没有可用的实例，它将返回null。例如，当流水线队列生产者比消费者慢，或者溢出的队列需要读入更多数据时，可能会发生这种情况。
     *
     * 重要提示：使用者必须确保每个缓冲区实例在用完后最终都会使用buffer.recycleBuffer（）进行回收。
     *
     * Returns the next {@link Buffer} instance of this queue iterator.
     *
     * <p>If there is currently no instance available, it will return <code>null</code>. This might
     * happen for example when a pipelined queue producer is slower than the consumer or a spilled
     * queue needs to read in more data.
     *
     * <p><strong>Important</strong>: The consumer has to make sure that each buffer instance will
     * eventually be recycled with {@link Buffer#recycleBuffer()} after it has been consumed.
     */
    @Nullable
    BufferAndBacklog getNextBuffer() throws IOException;

    void notifyDataAvailable();

    default void notifyPriorityEvent(int priorityBufferNumber) {}

    void releaseAllResources() throws IOException;

    boolean isReleased();

    void resumeConsumption();

    Throwable getFailureCause();

    boolean isAvailable(int numCreditsAvailable);

    int unsynchronizedGetNumberOfQueuedBuffers();
}
