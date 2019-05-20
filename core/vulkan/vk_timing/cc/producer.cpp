/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string.h>

#ifdef TARGET_OS_ANDROID
#include "perfetto/base/android_task_runner.h"
using TaskRunner = perfetto::base::AndroidTaskRunner;
#else
#include "perfetto/base/unix_task_runner.h"
using TaskRunner = perfetto::base::UnixTaskRunner;
#endif
#include "perfetto/trace/gpu/gpu_slice.pbzero.h"
#include "perfetto/trace/trace_packet.pbzero.h"
#include "perfetto/tracing/core/data_source_config.h"
#include "perfetto/tracing/core/data_source_descriptor.h"
#include "perfetto/tracing/core/producer.h"
#include "perfetto/tracing/core/trace_packet.h"
#include "perfetto/tracing/core/trace_writer.h"
#include "perfetto/tracing/ipc/producer_ipc_client.h"

#include "core/cc/log.h"
#include "core/cc/semaphore.h"

namespace timing {

namespace {

const char* kSocketName = "/dev/socket/traced_producer";
const char* kProducerName = "gpu.timing";
const char* kDataSourceName = "gpu.timing";

class TimingProducer : public perfetto::Producer {
 public:
  TimingProducer(perfetto::base::TaskRunner* task_runner)
      : task_runner_(task_runner) {}

  ~TimingProducer() override {}

  void OnConnect() override {
    GAPID_INFO("[producer] OnConnect");
    {
      perfetto::DataSourceDescriptor desc;
      desc.set_name(kDataSourceName);
      endpoint_->RegisterDataSource(desc);
    }
  }

  void OnTracingSetup() override { GAPID_INFO("[producer] OnTracingSetup"); }

  void SetupDataSource(perfetto::DataSourceInstanceID instance_id,
                       const perfetto::DataSourceConfig& config) override {
    GAPID_INFO("[producer] SetupDataSource id=%" PRIu64 ", name=%s",
               instance_id, config.name().c_str());
    auto buffer_id = static_cast<perfetto::BufferID>(config.target_buffer());
    writer_ = endpoint_->CreateTraceWriter(buffer_id);
  }

  void StartDataSource(perfetto::DataSourceInstanceID,
                       const perfetto::DataSourceConfig&) override {
    GAPID_INFO("[producer] StartDataSource");
    started = true;
  }

  void StopDataSource(perfetto::DataSourceInstanceID) override {
    GAPID_INFO("[producer] StopDataSource");
    started = false;
    writer_.reset();
  }

  void Flush(perfetto::FlushRequestID req_id,
             const perfetto::DataSourceInstanceID* data_source_ids,
             size_t num_data_sources) override {
    GAPID_INFO("[producer] Flush");
    perfetto::TracingService::ProducerEndpoint* ep = endpoint_.get();
    writer_->Flush([ep, req_id] { ep->NotifyFlushComplete(req_id); });
  }

  void OnDisconnect() override { GAPID_INFO("[producer] OnDisconnect"); }

  void Connect() {
    endpoint_ = perfetto::ProducerIPCClient::Connect(
        kSocketName, this, kProducerName, task_runner_);
  }

  void SendEvent(uint32_t pid, uint64_t queue_id, uint32_t queue_idx,
                 int64_t start_ts, int64_t end_ts, const char* label) {
    if (started && writer_ != nullptr) {
      auto packet = writer_->NewTracePacket();
      packet->set_timestamp(
          static_cast<uint64_t>(perfetto::base::GetBootTimeNs().count()));
      auto* gpu = packet->set_gpu_slice();
      gpu->set_pid(pid);
      gpu->set_queue_id(queue_id);
      gpu->set_queue_index(queue_idx);
      gpu->set_start_ts(start_ts);
      gpu->set_end_ts(end_ts);
      gpu->set_label(label);
    }
  }

  void Disconnect() {
    GAPID_INFO("[producer] Disconnecting.");
    writer_.reset();
    endpoint_.reset();
    started = false;
  }

 private:
  TimingProducer(const TimingProducer&) = delete;
  TimingProducer& operator=(const TimingProducer&) = delete;

  perfetto::base::TaskRunner* task_runner_;
  std::unique_ptr<perfetto::TracingService::ProducerEndpoint> endpoint_;
  std::unique_ptr<perfetto::TraceWriter> writer_;
  bool started;
};

class Perfetto {
 public:
  Perfetto() {
    GAPID_INFO("[producer] Starting perfetto.");
    core::Semaphore semaphore;
    thread_ = std::thread([this, &semaphore]() {
      task_runner_ = new TaskRunner();
      producer_ = new TimingProducer(task_runner_);
      producer_->Connect();
      semaphore.release();
      task_runner_->Run();
    });
    semaphore.acquire();
  }

  ~Perfetto() {
    GAPID_INFO("[producer] Exiting perfetto.");
    task_runner_->PostTask([=] { producer_->Disconnect(); });
    task_runner_->Quit();
    thread_.join();

    delete producer_;
    delete task_runner_;
  }

  void sendEvent(uint32_t pid, uint64_t queue_id, uint32_t queue_idx,
                 int64_t start_ts, int64_t end_ts, const char* label) {
    char* label_copy = strdup(label);
    task_runner_->PostTask([=] {
      producer_->SendEvent(pid, queue_id, queue_idx, start_ts, end_ts,
                           label_copy);
      free(label_copy);
    });
  }

 private:
  std::thread thread_;
  TaskRunner* task_runner_;
  TimingProducer* producer_;
};

Perfetto* getPerfetto() {
  static Perfetto instance;
  return &instance;
}

}  // namespace

void start_perfetto() { getPerfetto(); }

void send_event(uint32_t pid, uint64_t queue_id, uint32_t queue_idx,
                int64_t start_ts, int64_t end_ts, const char* label) {
  getPerfetto()->sendEvent(pid, queue_id, queue_idx, start_ts, end_ts, label);
}

}  // namespace timing
