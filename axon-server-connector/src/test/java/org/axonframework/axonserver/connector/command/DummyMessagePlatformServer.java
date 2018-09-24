/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.PlatformService;
import io.axoniq.axonserver.grpc.command.CommandProviderInbound;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.axoniq.axonserver.grpc.command.CommandServiceGrpc;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Author: marc
 */
public class DummyMessagePlatformServer {
    private final int port;
    private Server server;
    private Map<String, Set<StreamObserver>> subscriptions = new HashMap<>();

    public DummyMessagePlatformServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new CommandHandler())
                .addService(new PlatformService(port))
                .build();
        server.start();
    }

    public void stop() {
        subscriptions.clear();
        try {
            server.shutdownNow().awaitTermination();
        } catch (InterruptedException ignore) {
        }
    }

    public Set subscriptions(String query) {
        return subscriptions.get(query);
    }

    class CommandHandler extends CommandServiceGrpc.CommandServiceImplBase {

        @Override
        public StreamObserver<CommandProviderOutbound> openStream(StreamObserver<CommandProviderInbound> responseObserver) {
            return new StreamObserver<CommandProviderOutbound>() {
                @Override
                public void onNext(CommandProviderOutbound queryProviderOutbound) {
                    switch(queryProviderOutbound.getRequestCase()) {
                        case SUBSCRIBE:
                            subscriptions.computeIfAbsent(queryProviderOutbound.getSubscribe().getCommand(), k -> new HashSet<>()).add(responseObserver);
                            break;
                        case UNSUBSCRIBE:
                            Set<StreamObserver> connected = subscriptions.get(queryProviderOutbound.getUnsubscribe().getCommand());
                            if( connected != null) connected.remove(responseObserver);
                            break;
                        case FLOWCONTROL:
                            break;
                        case COMMANDRESPONSE:
                            break;
                        case REQUEST_NOT_SET:
                            break;
                    }
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {

                }
            };
        }

        @Override
        public void dispatch(Command request, StreamObserver<CommandResponse> responseObserver) {
            String data = request.getPayload().getData().toStringUtf8();
            if(data.contains("error")) {
                responseObserver.onNext(CommandResponse.newBuilder()
                        .setErrorCode(ErrorCode.DATAFILE_READ_ERROR.errorCode())
                        .setMessageIdentifier(request.getMessageIdentifier())
                        .setMessage(ErrorMessage.newBuilder().setMessage(data))
                        .build());

            } else {
                responseObserver.onNext(CommandResponse.newBuilder()
                        .setMessageIdentifier(request.getMessageIdentifier())
                        .setPayload(SerializedObject.newBuilder()
                                .setData(request.getPayload().getData())
                                .setType(String.class.getName())
                                .build())
                        .build());
            }
            responseObserver.onCompleted();
        }

    }

}
