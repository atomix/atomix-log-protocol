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
package io.atomix.protocols.log;

import java.util.concurrent.CompletableFuture;

import io.atomix.protocols.log.impl.DefaultDistributedLogClient;
import io.atomix.protocols.log.protocol.LogClientProtocol;
import io.atomix.service.client.LogClient;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.concurrent.ThreadModel;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Distributed log client.
 */
public interface DistributedLogClient extends LogClient {

  /**
   * Returns a new server builder.
   *
   * @return a new server builder
   */
  static Builder builder() {
    return new DefaultDistributedLogClient.Builder();
  }

  /**
   * Connects the log session.
   *
   * @return a future to be completed once the log session has been connected
   */
  CompletableFuture<DistributedLogClient> connect();

  /**
   * Closes the log session.
   *
   * @return a future to be completed once the log session has been closed
   */
  CompletableFuture<Void> close();

  /**
   * Distributed log client builder.
   */
  abstract class Builder implements io.atomix.utils.Builder<DistributedLogClient> {
    protected String clientId;
    protected LogClientProtocol protocol;
    protected TermProvider termProvider;
    protected ThreadModel threadModel = ThreadModel.SHARED_THREAD_POOL;
    protected int threadPoolSize = Math.max(Math.min(Runtime.getRuntime().availableProcessors() * 2, 16), 4);
    protected ThreadContextFactory threadContextFactory;

    /**
     * Sets the client ID.
     *
     * @param clientId The client ID.
     * @return The client builder.
     * @throws NullPointerException if {@code clientId} is null
     */
    public Builder withClientId(String clientId) {
      this.clientId = checkNotNull(clientId);
      return this;
    }

    /**
     * Sets the protocol.
     *
     * @param protocol the protocol
     * @return the client builder
     */
    public Builder withProtocol(LogClientProtocol protocol) {
      this.protocol = checkNotNull(protocol);
      return this;
    }

    /**
     * Sets the term provider.
     *
     * @param termProvider the term provider
     * @return the client builder
     */
    public Builder withTermProvider(TermProvider termProvider) {
      this.termProvider = checkNotNull(termProvider);
      return this;
    }

    /**
     * Sets the client thread model.
     *
     * @param threadModel the client thread model
     * @return the server builder
     * @throws NullPointerException if the thread model is null
     */
    public Builder withThreadModel(ThreadModel threadModel) {
      this.threadModel = checkNotNull(threadModel, "threadModel cannot be null");
      return this;
    }

    /**
     * Sets the client thread pool size.
     *
     * @param threadPoolSize The client thread pool size.
     * @return The server builder.
     * @throws IllegalArgumentException if the thread pool size is not positive
     */
    public Builder withThreadPoolSize(int threadPoolSize) {
      checkArgument(threadPoolSize > 0, "threadPoolSize must be positive");
      this.threadPoolSize = threadPoolSize;
      return this;
    }

    /**
     * Sets the client thread context factory.
     *
     * @param threadContextFactory the client thread context factory
     * @return the server builder
     * @throws NullPointerException if the factory is null
     */
    public Builder withThreadContextFactory(ThreadContextFactory threadContextFactory) {
      this.threadContextFactory = checkNotNull(threadContextFactory, "threadContextFactory cannot be null");
      return this;
    }
  }
}
