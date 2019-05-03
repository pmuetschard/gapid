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
#else
#include "perfetto/base/unix_task_runner.h"
#endif
#include "perfetto/tracing/core/data_source_config.h"
#include "perfetto/tracing/core/data_source_descriptor.h"
#include "perfetto/tracing/core/producer.h"
#include "perfetto/tracing/core/trace_packet.h"
#include "perfetto/tracing/core/trace_writer.h"
#include "perfetto/tracing/ipc/producer_ipc_client.h"
#include "perfetto/trace/gpu/gpu_slice.pbzero.h"
#include "perfetto/trace/trace_packet.pbzero.h"

#include "core/cc/log.h"

namespace {

const char* kSocketName = "/dev/socket/traced_producer";
const char* kProducerName = "gpu.timing";
const char* kDataSourceName = "gpu.timing";

class TimingProducer : public perfetto::Producer {
 public:
  TimingProducer(perfetto::base::TaskRunner* task_runner) :
    task_runner_(task_runner) {
  }

  ~TimingProducer() override {
  }

  void OnConnect() override {
    GAPID_INFO("[producer] OnConnect");
    {
      perfetto::DataSourceDescriptor desc;
      desc.set_name(kDataSourceName);
      endpoint_->RegisterDataSource(desc);
    }
  }

  void OnTracingSetup() override {
    GAPID_INFO("[producer] OnTracingSetup");
  }

  void SetupDataSource(perfetto::DataSourceInstanceID instance_id, const perfetto::DataSourceConfig& config) override {
    GAPID_INFO("[producer] SetupDataSource id=%" PRIu64 ", name=%s", instance_id, config.name().c_str());
    auto buffer_id = static_cast<perfetto::BufferID>(config.target_buffer());
    writer_ = endpoint_->CreateTraceWriter(buffer_id);
  }

  void StartDataSource(perfetto::DataSourceInstanceID, const perfetto::DataSourceConfig&) override {
    GAPID_INFO("[producer] StartDataSource");
    started = true;
  }

  void StopDataSource(perfetto::DataSourceInstanceID) override {
    GAPID_INFO("[producer] StopDataSource");
    started = false;
    writer_.release();
  }

  void Flush(perfetto::FlushRequestID req_id,
      const perfetto::DataSourceInstanceID* data_source_ids,
      size_t num_data_sources) override {
    GAPID_INFO("[producer] Flush");
    perfetto::TracingService::ProducerEndpoint* ep = endpoint_.get();
    writer_->Flush([ep, req_id] {
      ep->NotifyFlushComplete(req_id);
    });
  }

  void OnDisconnect() override {
    GAPID_INFO("[producer] OnDisconnect");
  }

  void Connect() {
    endpoint_ = perfetto::ProducerIPCClient::Connect(
        kSocketName, this, kProducerName, task_runner_);
  }

  void SendEvent(int32_t pid, uint64_t queue_id, uint32_t queue_idx, uint64_t start_ts, uint64_t end_ts, const char* label) {
    if (started && writer_ != nullptr) {
      auto packet = writer_->NewTracePacket();
      packet->set_timestamp(static_cast<uint64_t>(perfetto::base::GetBootTimeNs().count()));
      auto* gpu = packet->set_gpu_slice();
      gpu->set_pid(pid);
      gpu->set_queue_id(queue_id);
      gpu->set_queue_index(queue_idx);
      gpu->set_start_ts(start_ts);
      gpu->set_end_ts(end_ts);
      gpu->set_label(label);
    }
  }

 private:
  TimingProducer(const TimingProducer&) = delete;
  TimingProducer& operator=(const TimingProducer&) = delete;

  perfetto::base::TaskRunner* task_runner_;
  std::unique_ptr<perfetto::TracingService::ProducerEndpoint> endpoint_;
  std::unique_ptr<perfetto::TraceWriter> writer_;
  bool started;
};

#ifdef TARGET_OS_ANDROID
perfetto::base::AndroidTaskRunner* get_task_runner() {
  static perfetto::base::AndroidTaskRunner task_runner;
  return &task_runner;
}
#else
perfetto::base::UnixTaskRunner* get_task_runner() {
  static perfetto::base::UnixTaskRunner task_runner;
  return &task_runner;
}
#endif

TimingProducer* get_producer() {
  static TimingProducer producer(get_task_runner());
  return &producer;
}

void perfetto_main() {
  get_producer()->Connect();
  get_task_runner()->Run();
  GAPID_INFO("[producer] Thread exiting.");
}

__attribute__((constructor)) void _startup() {
  GAPID_INFO("[producer] Starting thread.");
  static std::thread thr(perfetto_main);
}

}  // namespace


extern "C" {

__attribute__((visibility("default"))) void send_event(int32_t pid, uint64_t queue_id, uint32_t queue_idx, uint64_t start_ts, uint64_t end_ts, const char* label) {
  char* label_copy = strdup(label);
  get_task_runner()->PostTask([=]{
    get_producer()->SendEvent(pid, queue_id, queue_idx, start_ts, end_ts, label_copy);
    free(label_copy);
  });
}

}
