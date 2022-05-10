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

package org.apache.flink.runtime.io.network.netty;

import org.apache.flink.runtime.io.network.NetworkSequenceViewReader;
import org.apache.flink.runtime.io.network.TaskEventPublisher;
import org.apache.flink.runtime.io.network.netty.NettyMessage.AddCredit;
import org.apache.flink.runtime.io.network.netty.NettyMessage.CancelPartitionRequest;
import org.apache.flink.runtime.io.network.netty.NettyMessage.CloseRequest;
import org.apache.flink.runtime.io.network.netty.NettyMessage.ResumeConsumption;
import org.apache.flink.runtime.io.network.partition.PartitionNotFoundException;
import org.apache.flink.runtime.io.network.partition.ResultPartitionProvider;
import org.apache.flink.runtime.io.network.partition.consumer.InputChannelID;

import org.apache.flink.shaded.netty4.io.netty.channel.ChannelHandlerContext;
import org.apache.flink.shaded.netty4.io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.flink.runtime.io.network.netty.NettyMessage.PartitionRequest;
import static org.apache.flink.runtime.io.network.netty.NettyMessage.TaskEventRequest;

/**
 * 通道处理程序，用于启动数据传输和调度向后流动的任务事件。
 * Channel handler to initiate data transfers and dispatch backwards flowing task events.
 */
class PartitionRequestServerHandler extends SimpleChannelInboundHandler<NettyMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(PartitionRequestServerHandler.class);

    private final ResultPartitionProvider partitionProvider;

    private final TaskEventPublisher taskEventPublisher;

    private final PartitionRequestQueue outboundQueue;

    PartitionRequestServerHandler(
            ResultPartitionProvider partitionProvider,
            TaskEventPublisher taskEventPublisher,
            PartitionRequestQueue outboundQueue) {

        this.partitionProvider = partitionProvider;
        this.taskEventPublisher = taskEventPublisher;
        this.outboundQueue = outboundQueue;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyMessage msg) throws Exception {
        try {
            Class<?> msgClazz = msg.getClass();

            // 类似spark中的偏函数，分情况处理，这里就没用rpc实现的动态代理
            // ----------------------------------------------------------------
            // 请求Partition数据
            // Intermediate result partition requests
            // ----------------------------------------------------------------
            if (msgClazz == PartitionRequest.class) {
                // 下游请求Partition数据
                PartitionRequest request = (PartitionRequest) msg;

                LOG.debug("Read channel on {}: {}.", ctx.channel().localAddress(), request);

                try {
                    /**
                     * 1、接收到客户端的PartitionRequest之后，会给这个请求创建一个reader，在这里我们只看基于Credit的CreditBasedSequenceNumberingViewReader，
                     * 每个reader都有一个初始凭据credit，值等于消费端RemoteInputChannel的独占buffer数。
                     * 2、这个reader随后会创建一个ResultSubpartitionView，reader就是通过这个ResultSubpartitionView来从对应的ResultSubpartition里读取数据，
                     * 在实时计算里，这个ResultSubpartitionView是PipelinedSubpartitionView的实例。
                     *
                     * 3、我们可以分析一下，一个上游（Map）任务对应一个ResultPartition，每个ResultPartition有多个ResultSubPartition，每个ResultSubPartition对应一个下游（Reduce）任务，每个下游任务都会来请求ResultPartition里的一个ResultSubPartition。
                     * 比如有10个ResultSubPartition，就有10个Reduce任务，每个Reduce发起一个PartitionRequest，Map端就会创建10个reader，每个reader读取一个ResultSubPartition。
                     *
                     * 这里并没有实际返回数据，下游请求ResultSubPartition数据后，上游会主动向下游发送数据，flink数据传输采用的push的方式，这个和spark不同，这是流处理和批处理的特性确定的
                     */
                    NetworkSequenceViewReader reader;
                    reader =
                            new CreditBasedSequenceNumberingViewReader(
                                    request.receiverId, request.credit, outboundQueue);

                    reader.requestSubpartitionView(
                            partitionProvider, request.partitionId, request.queueIndex);

                    outboundQueue.notifyReaderCreated(reader);
                } catch (PartitionNotFoundException notFound) {
                    respondWithError(ctx, notFound, request.receiverId);
                }
            }
            // ----------------------------------------------------------------
            // 下面都是PartitionRequest相关的事件
            // Task events
            // ----------------------------------------------------------------
            else if (msgClazz == TaskEventRequest.class) {
                TaskEventRequest request = (TaskEventRequest) msg;

                if (!taskEventPublisher.publish(request.partitionId, request.event)) {
                    respondWithError(
                            ctx,
                            new IllegalArgumentException("Task event receiver not found."),
                            request.receiverId);
                }
            } else if (msgClazz == CancelPartitionRequest.class) {
                CancelPartitionRequest request = (CancelPartitionRequest) msg;

                outboundQueue.cancel(request.receiverId);
            } else if (msgClazz == CloseRequest.class) {
                outboundQueue.close();
            } else if (msgClazz == AddCredit.class) {
                AddCredit request = (AddCredit) msg;

                outboundQueue.addCreditOrResumeConsumption(
                        request.receiverId, reader -> reader.addCredit(request.credit));
            } else if (msgClazz == ResumeConsumption.class) {
                ResumeConsumption request = (ResumeConsumption) msg;

                outboundQueue.addCreditOrResumeConsumption(
                        request.receiverId, NetworkSequenceViewReader::resumeConsumption);
            } else {
                LOG.warn("Received unexpected client request: {}", msg);
            }
        } catch (Throwable t) {
            respondWithError(ctx, t);
        }
    }

    private void respondWithError(ChannelHandlerContext ctx, Throwable error) {
        ctx.writeAndFlush(new NettyMessage.ErrorResponse(error));
    }

    private void respondWithError(
            ChannelHandlerContext ctx, Throwable error, InputChannelID sourceId) {
        LOG.debug("Responding with error: {}.", error.getClass());

        ctx.writeAndFlush(new NettyMessage.ErrorResponse(error, sourceId));
    }
}
