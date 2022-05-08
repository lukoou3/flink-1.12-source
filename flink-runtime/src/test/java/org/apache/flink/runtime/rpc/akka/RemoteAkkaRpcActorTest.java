/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.rpc.akka;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.rpc.RpcUtils;
import org.apache.flink.util.SerializedValue;
import org.apache.flink.util.TestLogger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/** Tests for remote AkkaRpcActors. */
public class RemoteAkkaRpcActorTest extends TestLogger {
    // RpcService类似于spark的RpcEnv
    private static AkkaRpcService rpcService;
    private static AkkaRpcService otherRpcService;

    @BeforeClass
    public static void setupClass() throws Exception {
        final Configuration configuration = new Configuration();
        rpcService =
                AkkaRpcServiceUtils.createRemoteRpcService(
                        configuration, "localhost", "0", null, Optional.empty());

        otherRpcService =
                AkkaRpcServiceUtils.createRemoteRpcService(
                        configuration, "localhost", "0", null, Optional.empty());
    }

    @AfterClass
    public static void teardownClass()
            throws InterruptedException, ExecutionException, TimeoutException {
        RpcUtils.terminateRpcServices(Time.seconds(10), rpcService, otherRpcService);
    }

    @Test
    public void canRespondWithNullValueRemotely() throws Exception {
        try (final AkkaRpcActorTest.NullRespondingEndpoint nullRespondingEndpoint =
                new AkkaRpcActorTest.NullRespondingEndpoint(rpcService)) {
            nullRespondingEndpoint.start();

            final AkkaRpcActorTest.NullRespondingGateway rpcGateway =
                    otherRpcService
                            .connect(
                                    nullRespondingEndpoint.getAddress(),
                                    AkkaRpcActorTest.NullRespondingGateway.class)
                            .join();

            final CompletableFuture<Integer> nullValuedResponseFuture = rpcGateway.foobar();

            assertThat(nullValuedResponseFuture.join(), is(nullValue()));
        }
    }

    @Test
    public void canRespondWithSynchronousNullValueRemotely() throws Exception {
        try (final AkkaRpcActorTest.NullRespondingEndpoint nullRespondingEndpoint =
                new AkkaRpcActorTest.NullRespondingEndpoint(rpcService)) {
            nullRespondingEndpoint.start();

            final AkkaRpcActorTest.NullRespondingGateway rpcGateway =
                    otherRpcService
                            .connect(
                                    nullRespondingEndpoint.getAddress(),
                                    AkkaRpcActorTest.NullRespondingGateway.class)
                            .join();

            final Integer value = rpcGateway.synchronousFoobar();

            assertThat(value, is(nullValue()));
        }
    }

    /**
     * RpcService类似于spark的RpcEnv
     * RpcEndpoint类似于spark的RpcEndpoint，创建spark的RpcEndpoint需要传入RpcEnv，同样创建flink的RpcEndpoint需要传入RpcService
     * RpcGateway类似于spark的RpcEndpointRef
     *      不同的是saprk的ref不需要为每个RpcEndpointRef新建不同的类，两边通过约定发送send/ask请求，服务端通过模式匹配处理请求
     *      flink的gateway需要为每个RpcGateway新建不同的接口，服务端实现这个接口，客户端获取这个RpcGateway的代理对象，直接调用特定的方法
     *      应该是由scala和java语言的特性决定的吧，scala使用模式匹配偏函数易用开发效率快，java不太擅长这种处理
     *
     */
    @Test
    public void canRespondWithSerializedValueRemotely() throws Exception {
        try (final AkkaRpcActorTest.SerializedValueRespondingEndpoint endpoint =
                new AkkaRpcActorTest.SerializedValueRespondingEndpoint(rpcService)) {
            // 这咋还需要收到调用
            endpoint.start();

            final AkkaRpcActorTest.SerializedValueRespondingGateway remoteGateway =
                    otherRpcService
                            .connect(
                                    endpoint.getAddress(),
                                    AkkaRpcActorTest.SerializedValueRespondingGateway.class)
                            .join();

            SerializedValue<String> response = remoteGateway.getSerializedValueSynchronously();
            System.out.println(response);
            assertThat(
                    remoteGateway.getSerializedValueSynchronously(),
                    equalTo(AkkaRpcActorTest.SerializedValueRespondingEndpoint.SERIALIZED_VALUE));

            final CompletableFuture<SerializedValue<String>> responseFuture =
                    remoteGateway.getSerializedValue();

            assertThat(
                    responseFuture.get(),
                    equalTo(AkkaRpcActorTest.SerializedValueRespondingEndpoint.SERIALIZED_VALUE));
        }
    }
}
