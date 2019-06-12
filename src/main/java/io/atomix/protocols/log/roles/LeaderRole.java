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
package io.atomix.protocols.log.roles;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Maps;
import io.atomix.protocols.log.DistributedLogServer;
import io.atomix.protocols.log.LogEntry;
import io.atomix.protocols.log.impl.DistributedLogServerContext;
import io.atomix.protocols.log.protocol.AppendRequest;
import io.atomix.protocols.log.protocol.AppendResponse;
import io.atomix.protocols.log.protocol.BackupOperation;
import io.atomix.protocols.log.protocol.ConsumeRequest;
import io.atomix.protocols.log.protocol.ConsumeResponse;
import io.atomix.protocols.log.protocol.LogRecord;
import io.atomix.protocols.log.protocol.ResetRequest;
import io.atomix.protocols.log.protocol.ResponseStatus;
import io.atomix.storage.StorageException;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader;
import io.atomix.utils.stream.StreamHandler;

/**
 * Primary role.
 */
public class LeaderRole extends LogServerRole {
  private final Replicator replicator;
  private final Map<ConsumerKey, ConsumerSender> consumers = Maps.newHashMap();

  public LeaderRole(DistributedLogServerContext context) {
    super(DistributedLogServer.Role.LEADER, context);
    switch (context.replicationStrategy()) {
      case SYNCHRONOUS:
        replicator = new SynchronousReplicator(context, log);
        break;
      case ASYNCHRONOUS:
        replicator = new AsynchronousReplicator(context, log);
        break;
      default:
        throw new AssertionError();
    }
  }

  @Override
  public CompletableFuture<AppendResponse> append(AppendRequest request) {
    logRequest(request);
    try {
      Indexed<LogEntry> entry = context.journal().writer().append(LogEntry.newBuilder()
          .setTerm(context.currentTerm())
          .setTimestamp(System.currentTimeMillis())
          .setValue(request.getValue())
          .build());
      return replicator.replicate(BackupOperation.newBuilder()
          .setIndex(entry.index())
          .setTerm(entry.entry().getTerm())
          .setTimestamp(entry.entry().getTimestamp())
          .setValue(entry.entry().getValue())
          .build())
          .thenApply(v -> {
            consumers.values().forEach(consumer -> consumer.next());
            return logResponse(AppendResponse.newBuilder()
                .setStatus(ResponseStatus.OK)
                .setIndex(entry.index())
                .build());
          });
    } catch (StorageException e) {
      return CompletableFuture.completedFuture(logResponse(AppendResponse.newBuilder()
          .setStatus(ResponseStatus.ERROR)
          .build()));
    }
  }

  @Override
  public CompletableFuture<Void> consume(ConsumeRequest request, StreamHandler<ConsumeResponse> handler) {
    logRequest(request);
    JournalReader<LogEntry> reader = context.journal().openReader(request.getIndex(), JournalReader.Mode.COMMITS);
    ConsumerSender consumer = new ConsumerSender(request.getMemberId(), request.getConsumerId(), reader, handler);
    consumers.put(new ConsumerKey(request.getMemberId(), request.getConsumerId()), consumer);
    consumer.next();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void reset(ResetRequest request) {
    logRequest(request);
    ConsumerSender consumer = consumers.get(new ConsumerKey(request.getMemberId(), request.getConsumerId()));
    if (consumer != null) {
      consumer.reset(request.getIndex());
    }
  }

  @Override
  public void close() {
    replicator.close();
    consumers.values().forEach(consumer -> consumer.close());
  }

  /**
   * Consumer sender.
   */
  class ConsumerSender {
    private final String memberId;
    private final long consumerId;
    private final JournalReader<LogEntry> reader;
    private final StreamHandler<ConsumeResponse> handler;
    private boolean open = true;

    ConsumerSender(String memberId, long consumerId, JournalReader<LogEntry> reader, StreamHandler<ConsumeResponse> handler) {
      this.memberId = memberId;
      this.consumerId = consumerId;
      this.reader = reader;
      this.handler = handler;
    }

    /**
     * Resets the consumer to the given index.
     *
     * @param index the index to which to reset the consumer
     */
    void reset(long index) {
      reader.reset(index);
      next();
    }

    /**
     * Sends the next batch to the consumer.
     */
    void next() {
      if (!open) {
        return;
      }
      context.threadContext().execute(() -> {
        if (reader.hasNext()) {
          Indexed<LogEntry> entry = reader.next();
          LogRecord record = LogRecord.newBuilder()
              .setIndex(entry.index())
              .setTimestamp(entry.entry().getTimestamp())
              .setValue(entry.entry().getValue())
              .build();
          boolean reset = reader.getFirstIndex() == entry.index();
          ConsumeResponse response = ConsumeResponse.newBuilder()
              .setRecord(record)
              .setReset(reset)
              .build();
          log.trace("Sending {} to {} at {}", response, memberId, consumerId);
          handler.next(response);
          next();
        }
      });
    }

    /**
     * Closes the consumer.
     */
    void close() {
      reader.close();
      handler.complete();
      open = false;
    }
  }

  /**
   * Consumer key.
   */
  class ConsumerKey {
    private final String memberId;
    private final long consumerId;

    ConsumerKey(String memberId, long consumerId) {
      this.memberId = memberId;
      this.consumerId = consumerId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(memberId, consumerId);
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof ConsumerKey) {
        ConsumerKey that = (ConsumerKey) object;
        return this.memberId.equals(that.memberId) && this.consumerId == that.consumerId;
      }
      return false;
    }
  }
}
