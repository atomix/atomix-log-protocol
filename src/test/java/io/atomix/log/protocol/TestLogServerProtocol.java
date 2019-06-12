/*
 * Copyright 2019-present Open Networking Foundation
 *
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
package io.atomix.log.protocol;

import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import io.atomix.protocols.log.protocol.AppendRequest;
import io.atomix.protocols.log.protocol.AppendResponse;
import io.atomix.protocols.log.protocol.BackupRequest;
import io.atomix.protocols.log.protocol.BackupResponse;
import io.atomix.protocols.log.protocol.ConsumeRequest;
import io.atomix.protocols.log.protocol.ConsumeResponse;
import io.atomix.protocols.log.protocol.LogServerProtocol;
import io.atomix.protocols.log.protocol.ResetRequest;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.stream.StreamHandler;

/**
 * Test server protocol.
 */
public class TestLogServerProtocol extends TestLogProtocol implements LogServerProtocol {
  private volatile Function<AppendRequest, CompletableFuture<AppendResponse>> appendHandler;
  private volatile Function<BackupRequest, CompletableFuture<BackupResponse>> backupHandler;
  private volatile BiFunction<ConsumeRequest, StreamHandler<ConsumeResponse>, CompletableFuture<Void>> consumeHandler;
  private volatile Consumer<ResetRequest> resetConsumer;

  public TestLogServerProtocol(String memberId, Map<String, TestLogServerProtocol> servers, Map<String, TestLogClientProtocol> clients) {
    super(servers, clients);
    servers.put(memberId, this);
  }

  private CompletableFuture<TestLogServerProtocol> getServer(String memberId) {
    TestLogServerProtocol server = server(memberId);
    if (server != null) {
      return Futures.completedFuture(server);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  private CompletableFuture<TestLogClientProtocol> getClient(String memberId) {
    TestLogClientProtocol client = client(memberId);
    if (client != null) {
      return Futures.completedFuture(client);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public CompletableFuture<BackupResponse> backup(String memberId, BackupRequest request) {
    return getServer(memberId).thenCompose(server -> server.backup(request));
  }

  CompletableFuture<AppendResponse> append(AppendRequest request) {
    Function<AppendRequest, CompletableFuture<AppendResponse>> appendHandler = this.appendHandler;
    if (appendHandler != null) {
      return appendHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<Void> consume(ConsumeRequest request, StreamHandler<ConsumeResponse> handler) {
    BiFunction<ConsumeRequest, StreamHandler<ConsumeResponse>, CompletableFuture<Void>> consumeHandler = this.consumeHandler;
    if (consumeHandler != null) {
      return consumeHandler.apply(request, handler);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  void reset(ResetRequest request) {
    Consumer<ResetRequest> resetConsumer = this.resetConsumer;
    if (resetConsumer != null) {
      resetConsumer.accept(request);
    }
  }

  CompletableFuture<BackupResponse> backup(BackupRequest request) {
    Function<BackupRequest, CompletableFuture<BackupResponse>> backupHandler = this.backupHandler;
    if (backupHandler != null) {
      return backupHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerAppendHandler(Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    this.appendHandler = handler;
  }

  @Override
  public void unregisterAppendHandler() {
    this.appendHandler = null;
  }

  @Override
  public void registerBackupHandler(Function<BackupRequest, CompletableFuture<BackupResponse>> handler) {
    this.backupHandler = handler;
  }

  @Override
  public void unregisterBackupHandler() {
    this.backupHandler = null;
  }

  @Override
  public void registerConsumeHandler(BiFunction<ConsumeRequest, StreamHandler<ConsumeResponse>, CompletableFuture<Void>> handler) {
    this.consumeHandler = handler;
  }

  @Override
  public void unregisterConsumeHandler() {
    this.consumeHandler = null;
  }

  @Override
  public void registerResetConsumer(Consumer<ResetRequest> consumer, Executor executor) {
    this.resetConsumer = request -> executor.execute(() -> consumer.accept(request));
  }

  @Override
  public void unregisterResetConsumer() {
    this.resetConsumer = null;
  }
}
