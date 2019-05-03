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

#include <inttypes.h>
#include <unistd.h>
#include <vulkan/vulkan.h>
#include <thread>
#include <vector>

#include "core/cc/log.h"

#include "layer.h"
#include "producer.h"

// Simple assumption is that there are no more than 4096
// simultaneous queue submissions
#define MAX_QUERIES 4096

namespace timing {

namespace {

static inline void set_dispatch_from_parent(void* child, void* parent) {
  *((const void**)child) = *((const void**)parent);
}

using TimeNanos = std::chrono::nanoseconds;
inline TimeNanos FromPosixTimespec(const struct timespec& ts) {
  return TimeNanos(ts.tv_sec * 1000000000LL + ts.tv_nsec);
}

static inline TimeNanos GetTimeInternalNs() {
  struct timespec ts = {};
  clock_gettime(CLOCK_BOOTTIME, &ts);
  return FromPosixTimespec(ts);
}

const uint32_t TRACE_TYPE_START = 0;
const uint32_t TRACE_TYPE_END = 1;
const uint32_t TRACE_TYPE_START_GPU = 2;
const uint32_t TRACE_TYPE_END_GPU = 3;

static void trace_write(VkQueue queue, uint32_t queue_idx, uint64_t start_ts,
                        uint64_t end_ts, const char* label) {
  static pid_t pid = getpid();

  send_event(pid, reinterpret_cast<uintptr_t>(queue), queue_idx, start_ts,
             end_ts, label);
}

static void startListeningThread(const DeviceData& deviceObj, QueueData* qd) {
  auto waitforfences = deviceObj.vkWaitForFences;
  auto getquerypoolresults = deviceObj.vkGetQueryPoolResults;
  auto freecommandbuffer = deviceObj.vkFreeCommandBuffers;
  auto destroyfence = deviceObj.vkDestroyFence;
  auto getfencestatus = deviceObj.vkGetEventStatus;
  auto setevent = deviceObj.vkSetEvent;
  auto queuewaitidle = deviceObj.vkQueueWaitIdle;
  auto resetevent = deviceObj.vkResetEvent;

  qd->thr = std::thread([qd, waitforfences, freecommandbuffer,
                         getquerypoolresults, destroyfence, getfencestatus,
                         setevent, queuewaitidle, resetevent]() {
    while (true) {
      qd->semaphore.acquire();
      qd->spinlock.Lock();
      if (qd->exiting) {
        qd->spinlock.Unlock();
        return;
      }
      SubmitData data = qd->waitData.front();
      qd->waitData.pop_front();
      qd->spinlock.Unlock();

      if (data.reset_timing) {
        while (getfencestatus(qd->device, qd->cpuWaitEvent) ==
               VkResult::VK_EVENT_RESET) {
          // burn cycles
        }
        setevent(qd->device, qd->gpuWaitEvent);
        while (getfencestatus(qd->device, qd->cpu2WaitEvent) ==
               VkResult::VK_EVENT_RESET) {
          // burn cycles
        }
        waitforfences(qd->device, 1, &data.fence, true, UINT64_MAX);

        auto afterTs = GetTimeInternalNs();
        int64_t device_time;
        getquerypoolresults(qd->device, qd->queryPool, data.index, 1, 8,
                            &device_time, 8,
                            VkQueryResultFlagBits::VK_QUERY_RESULT_64_BIT |
                                VK_QUERY_RESULT_WAIT_BIT);
        qd->drift = afterTs.count() - (int64_t)(qd->ts_period * device_time);
        resetevent(qd->device, qd->gpuWaitEvent);
        resetevent(qd->device, qd->cpuWaitEvent);
        resetevent(qd->device, qd->cpu2WaitEvent);

        qd->spinlock.Lock();
        freecommandbuffer(qd->device, qd->commandPool, 1,
                          &data.begin_command_buffer);
        qd->syncing = false;
        qd->spinlock.Unlock();
        destroyfence(qd->device, data.fence, nullptr);

        GAPID_INFO("------------ DRIFT: %" PRId64 ",%" PRId64 ",%" PRId64,
                   qd->drift, (int64_t)(qd->ts_period * device_time),
                   (int64_t)afterTs.count());
        continue;
      }

      waitforfences(qd->device, 1, &data.fence, true, UINT64_MAX);
      uint64_t out_data[2];
      getquerypoolresults(qd->device, qd->queryPool, data.index, 2, 16,
                          out_data, 8,
                          VkQueryResultFlagBits::VK_QUERY_RESULT_64_BIT |
                              VK_QUERY_RESULT_WAIT_BIT);
      char buff[1024];
      snprintf(buff, 1024, "CommandBuffer:%" PRIXPTR,
               (uintptr_t)data.submitted_command_buffer);
      uint64_t begin = qd->drift + (int64_t)(out_data[0] * qd->ts_period);
      uint64_t end = qd->drift + (int64_t)(out_data[1] * qd->ts_period);

      trace_write(qd->queue, qd->queue_family_index << 16 | qd->queue_index,
                  begin, end, buff);

      qd->spinlock.Lock();
      freecommandbuffer(qd->device, qd->commandPool, 1,
                        &data.begin_command_buffer);
      freecommandbuffer(qd->device, qd->commandPool, 1,
                        &data.end_command_buffer);
      qd->spinlock.Unlock();
      destroyfence(qd->device, data.fence, nullptr);
    }
  });
}

static void sendSync(VkDevice device, const DeviceData& deviceObj,
                     QueueData* qd, VkQueue queue) {
  VkCommandBuffer buffer;
  VkCommandBufferAllocateInfo allocate = {
      VkStructureType::VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, nullptr,
      qd->commandPool, VkCommandBufferLevel::VK_COMMAND_BUFFER_LEVEL_PRIMARY,
      1};
  deviceObj.vkAllocateCommandBuffers(device, &allocate, &buffer);
  set_dispatch_from_parent((void*)buffer, (void*)device);

  VkCommandBufferBeginInfo command_buffer_begin_info = {
      VkStructureType::VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
      nullptr,
      VkCommandBufferUsageFlagBits::VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
      nullptr,
  };
  deviceObj.vkBeginCommandBuffer(buffer, &command_buffer_begin_info);
  uint32_t begin_index = qd->last_query_index;
  deviceObj.vkCmdResetQueryPool(buffer, qd->queryPool, begin_index, 1);
  deviceObj.vkCmdSetEvent(
      buffer, qd->cpuWaitEvent,
      VkPipelineStageFlagBits::VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
  deviceObj.vkCmdWaitEvents(
      buffer, 1, &qd->gpuWaitEvent,
      VkPipelineStageFlagBits::VK_PIPELINE_STAGE_HOST_BIT,
      VkPipelineStageFlagBits::VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT, 0, nullptr,
      0, nullptr, 0, nullptr);
  deviceObj.vkCmdSetEvent(
      buffer, qd->cpu2WaitEvent,
      VkPipelineStageFlagBits::VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
  deviceObj.vkCmdWriteTimestamp(
      buffer, VkPipelineStageFlagBits::VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
      qd->queryPool, begin_index);
  qd->last_query_index = (begin_index + 1) % MAX_QUERIES;
  deviceObj.vkEndCommandBuffer(buffer);

  VkFence newFence;
  VkFenceCreateInfo fci = VkFenceCreateInfo{
      VkStructureType::VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, nullptr, 0};
  deviceObj.vkCreateFence(device, &fci, nullptr, &newFence);

  VkSubmitInfo newSubmit =
      VkSubmitInfo{VkStructureType::VK_STRUCTURE_TYPE_SUBMIT_INFO,
                   nullptr,
                   0,
                   nullptr,
                   nullptr,
                   1,
                   &buffer,
                   0,
                   nullptr};

  deviceObj.vkQueueSubmit(queue, 1, &newSubmit, newFence);
  SubmitData nsd = SubmitData{newFence, begin_index, buffer, 0, 0, true};

  qd->spinlock.Lock();
  qd->last_time_sync = GetTimeInternalNs();
  qd->syncing = true;
  qd->waitData.push_back(nsd);
  qd->spinlock.Unlock();
  qd->semaphore.release();
}

}  // namespace

void RegisterInstance(VkInstance instance, const InstanceData& data) {
  uint32_t num_devices = 0;
  data.vkEnumeratePhysicalDevices(instance, &num_devices, nullptr);

  std::vector<VkPhysicalDevice> physical_devices(num_devices);
  data.vkEnumeratePhysicalDevices(instance, &num_devices,
                                  physical_devices.data());

  auto physical_device_map = GetGlobalContext().GetPhysicalDeviceMap();

  for (VkPhysicalDevice physical_device : physical_devices) {
    PhysicalDeviceData pdd = {
        .instance = instance,
    };
    (*physical_device_map)[physical_device] = pdd;
  }
}

VKAPI_ATTR void VKAPI_CALL vkGetDeviceQueue(VkDevice device,
                                            uint32_t queueFamilyIndex,
                                            uint32_t queueIndex,
                                            VkQueue* pQueue) {
  PFN_vkGetDeviceQueue get_device_queue =
      GetGlobalContext().GetDeviceData(device)->vkGetDeviceQueue;
  get_device_queue(device, queueFamilyIndex, queueIndex, pQueue);

  auto queues = GetGlobalContext().GetQueueMap();
  if (queues->count(*pQueue) == 0) {
    auto devObject = GetGlobalContext().GetDeviceData(device);
    auto insObject = GetGlobalContext().GetInstanceData(devObject->instance);

    uint32_t queueFamilyPropertyCount = 0;
    insObject->vkGetPhysicalDeviceQueueFamilyProperties(
        devObject->physicalDevice, &queueFamilyPropertyCount, nullptr);
    if (queueFamilyIndex >= queueFamilyPropertyCount) {
      GAPID_FATAL("Invalid queue family");
      return;
    }

    VkPhysicalDeviceProperties* props =
        static_cast<VkPhysicalDeviceProperties*>(
            alloca(sizeof(VkPhysicalDeviceProperties)));
    insObject->vkGetPhysicalDeviceProperties(devObject->physicalDevice, props);

    VkQueueFamilyProperties* qfp = static_cast<VkQueueFamilyProperties*>(
        alloca(sizeof(VkQueueFamilyProperties) * queueFamilyPropertyCount));
    insObject->vkGetPhysicalDeviceQueueFamilyProperties(
        devObject->physicalDevice, &queueFamilyPropertyCount, qfp);
    bool supports_timestamps = qfp[queueFamilyIndex].timestampValidBits > 0;

    QueueData* qd = &(*queues)[*pQueue];
    qd->supports_timestamps = supports_timestamps;
    qd->queue = *pQueue;
    qd->device = device;
    qd->ts_period = props->limits.timestampPeriod;
    qd->queue_family_index = queueFamilyIndex;
    qd->queue_index = queueIndex;

    GAPID_INFO("*************************** ts_period: %g", qd->ts_period);

    {
      VkQueryPoolCreateInfo create_info = VkQueryPoolCreateInfo{
          VkStructureType::VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO,
          nullptr,
          0,
          VkQueryType::VK_QUERY_TYPE_TIMESTAMP,
          MAX_QUERIES,
          0};

      devObject->vkCreateQueryPool(device, &create_info, nullptr,
                                   &qd->queryPool);
    }

    {
      VkCommandPoolCreateInfo create_info = VkCommandPoolCreateInfo{
          VkStructureType::VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, nullptr,
          VkCommandPoolCreateFlagBits::VK_COMMAND_POOL_CREATE_TRANSIENT_BIT,
          queueFamilyIndex};

      devObject->vkCreateCommandPool(device, &create_info, nullptr,
                                     &qd->commandPool);
    }

    if (qd->supports_timestamps) {
      VkEventCreateInfo eci = VkEventCreateInfo{
          VkStructureType::VK_STRUCTURE_TYPE_EVENT_CREATE_INFO, nullptr, 0};
      devObject->vkCreateEvent(device, &eci, nullptr, &qd->gpuWaitEvent);
      devObject->vkCreateEvent(device, &eci, nullptr, &qd->cpuWaitEvent);
      devObject->vkCreateEvent(device, &eci, nullptr, &qd->cpu2WaitEvent);
      devObject->vkResetEvent(device, qd->gpuWaitEvent);
      devObject->vkResetEvent(device, qd->cpuWaitEvent);
      devObject->vkResetEvent(device, qd->cpu2WaitEvent);

      startListeningThread(*devObject, qd);
      sendSync(device, *devObject, qd, *pQueue);
    }
  }
}

VKAPI_ATTR VkResult VKAPI_CALL vkQueueSubmit(VkQueue queue,
                                             uint32_t submitCount,
                                             const VkSubmitInfo* submitInfo,
                                             VkFence fence) {
  auto qd = GetGlobalContext().GetQueueData(queue);
  auto device = qd->device;
  auto devObj = GetGlobalContext().GetDeviceData(device);

  if (submitCount == 0) {
    return devObj->vkQueueSubmit(queue, submitCount, submitInfo, fence);
  }

  qd->spinlock.Lock();
  bool needsSync = !qd->syncing &&
                   (GetTimeInternalNs() - qd->last_time_sync) >
                       std::chrono::nanoseconds(std::chrono::milliseconds(100));
  qd->spinlock.Unlock();

  if (needsSync) {
    sendSync(device, *devObj, &(*qd), queue);
  }

  for (size_t i = 0; i < submitCount; i++) {
    auto& si = submitInfo[i];
    if (si.commandBufferCount == 0) {
      auto ret = devObj->vkQueueSubmit(queue, submitCount, submitInfo, 0);
      if (ret != VkResult::VK_SUCCESS) {
        return ret;
      }
      continue;
    }

    size_t last = si.commandBufferCount - 1;
    for (size_t j = 0; j < si.commandBufferCount; j++) {
      VkCommandBuffer buffers[3];
      VkCommandBufferAllocateInfo allocate = {
          VkStructureType::VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
          nullptr, qd->commandPool,
          VkCommandBufferLevel::VK_COMMAND_BUFFER_LEVEL_PRIMARY, 2};
      qd->spinlock.Lock();
      devObj->vkAllocateCommandBuffers(device, &allocate, buffers);

      set_dispatch_from_parent((void*)buffers[0], (void*)device);
      set_dispatch_from_parent((void*)buffers[1], (void*)device);
      buffers[2] = buffers[1];
      buffers[1] = si.pCommandBuffers[j];

      VkCommandBufferBeginInfo command_buffer_begin_info = {
          VkStructureType::VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
          nullptr,
          VkCommandBufferUsageFlagBits::
              VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
          nullptr,
      };
      devObj->vkBeginCommandBuffer(buffers[0], &command_buffer_begin_info);
      devObj->vkBeginCommandBuffer(buffers[2], &command_buffer_begin_info);

      uint32_t begin_index = qd->last_query_index;
      if (begin_index >= MAX_QUERIES - 1) {
        // We need two queries. Since there is no implicit wrapping, just
        // skip the last one.
        begin_index = 0;
      }
      devObj->vkCmdResetQueryPool(buffers[0], qd->queryPool, begin_index, 2);
      devObj->vkCmdWriteTimestamp(
          buffers[0],
          VkPipelineStageFlagBits::VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
          qd->queryPool, begin_index);
      devObj->vkCmdWriteTimestamp(
          buffers[2],
          VkPipelineStageFlagBits::VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
          qd->queryPool, begin_index + 1);
      qd->last_query_index = (begin_index + 2) % MAX_QUERIES;

      devObj->vkEndCommandBuffer(buffers[0]);
      devObj->vkEndCommandBuffer(buffers[2]);
      qd->spinlock.Unlock();

      auto newSubmit = VkSubmitInfo{
          VkStructureType::VK_STRUCTURE_TYPE_SUBMIT_INFO,
          j == 0 ? si.pNext : nullptr,
          j == 0 ? si.waitSemaphoreCount : 0,
          j == 0 ? si.pWaitSemaphores : nullptr,
          j == 0 ? si.pWaitDstStageMask : nullptr,
          3,
          buffers,
          j == last ? si.signalSemaphoreCount : 0,
          j == last ? si.pSignalSemaphores : nullptr,
      };
      VkFence newFence;
      auto fci = VkFenceCreateInfo{
          VkStructureType::VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, nullptr, 0};
      devObj->vkCreateFence(device, &fci, nullptr, &newFence);
      auto ret = devObj->vkQueueSubmit(queue, 1, &newSubmit, newFence);
      if (ret != VkResult::VK_SUCCESS) {
        return ret;
      }

      auto nsd = SubmitData{newFence,   begin_index, buffers[0],
                            buffers[2], buffers[1],  false};
      qd->spinlock.Lock();
      qd->waitData.push_back(nsd);
      qd->spinlock.Unlock();
      qd->semaphore.release();
    }
  }

  if (fence != 0) {
    return devObj->vkQueueSubmit(queue, 0, nullptr, fence);
  }
  return VkResult::VK_SUCCESS;
}

}  // namespace timing
