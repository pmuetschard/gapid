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

#ifndef VK_TIMING_LAYER_H_
#define VK_TIMING_LAYER_H_

#include <deque>
#include <unordered_map>
#include "vulkan/vulkan.h"

#include "core/cc/recursive_spinlock.h"
#include "core/cc/semaphore.h"

#include "threading.h"

namespace timing {

struct InstanceData {
  PFN_vkGetInstanceProcAddr vkGetInstanceProcAddr;
  PFN_vkDestroyInstance vkDestroyInstance;

  PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices;
  PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties;
  PFN_vkGetPhysicalDeviceQueueFamilyProperties
      vkGetPhysicalDeviceQueueFamilyProperties;
};

struct PhysicalDeviceData {
  VkInstance instance;
};

struct DeviceData {
  PFN_vkGetDeviceProcAddr vkGetDeviceProcAddr;
  PFN_vkDestroyDevice vkDestroyDevice;

  PFN_vkGetDeviceQueue vkGetDeviceQueue;
  PFN_vkCreateQueryPool vkCreateQueryPool;
  PFN_vkCreateCommandPool vkCreateCommandPool;
  PFN_vkAllocateCommandBuffers vkAllocateCommandBuffers;
  PFN_vkFreeCommandBuffers vkFreeCommandBuffers;
  PFN_vkCreateEvent vkCreateEvent;
  PFN_vkResetEvent vkResetEvent;
  PFN_vkGetEventStatus vkGetEventStatus;
  PFN_vkSetEvent vkSetEvent;
  PFN_vkCreateFence vkCreateFence;
  PFN_vkWaitForFences vkWaitForFences;
  PFN_vkDestroyFence vkDestroyFence;
  PFN_vkQueueWaitIdle vkQueueWaitIdle;
  PFN_vkGetQueryPoolResults vkGetQueryPoolResults;
  PFN_vkBeginCommandBuffer vkBeginCommandBuffer;
  PFN_vkCmdResetQueryPool vkCmdResetQueryPool;
  PFN_vkCmdSetEvent vkCmdSetEvent;
  PFN_vkCmdWaitEvents vkCmdWaitEvents;
  PFN_vkCmdWriteTimestamp vkCmdWriteTimestamp;
  PFN_vkEndCommandBuffer vkEndCommandBuffer;
  PFN_vkQueueSubmit vkQueueSubmit;

  VkInstance instance;
  VkPhysicalDevice physicalDevice;
};

struct SubmitData {
  VkFence fence;
  uint32_t index;
  VkCommandBuffer begin_command_buffer;
  VkCommandBuffer end_command_buffer;
  VkCommandBuffer submitted_command_buffer;
  bool reset_timing;
};

struct QueueData {
  VkQueue queue;
  VkDevice device;
  VkQueryPool queryPool;
  VkCommandPool commandPool;
  core::RecursiveSpinLock spinlock;
  bool supports_timestamps;
  core::Semaphore semaphore;
  std::deque<SubmitData> waitData;
  std::thread thr;
  uint32_t last_query_index;
  float ts_period;
  int64_t drift;
  uint32_t queue_family_index;
  uint32_t queue_index;
  VkEvent gpuWaitEvent;
  VkEvent cpuWaitEvent;
  VkEvent cpu2WaitEvent;
  std::chrono::nanoseconds last_time_sync;
  bool syncing;
  bool exiting;

  ~QueueData() {
    spinlock.Lock();
    exiting = true;
    spinlock.Unlock();
    semaphore.release();
    thr.join();
  }
};

template <typename T>
struct ContextToken {
  ContextToken(T &object, threading::mutex &locker)
      : object_(object), context_lock_(locker) {}

  ContextToken(T &object, std::unique_lock<threading::mutex> &&locker)
      : object_(object), context_lock_(std::move(locker)) {}

  ContextToken(ContextToken &&_other)
      : object_(_other.object_),
        context_lock_(std::move(_other.context_lock_)) {}

  ContextToken(const ContextToken &_other) = delete;
  ContextToken &operator=(const ContextToken &_other) = delete;

  const T *operator->() const { return &object_; }
  const T &operator*() const { return object_; }
  T *operator->() { return &object_; }
  T &operator*() { return object_; }

 private:
  T &object_;
  std::unique_lock<threading::mutex> context_lock_;
};

struct Context {
  using InstanceMap = std::unordered_map<VkInstance, InstanceData>;
  using PhysicalDeviceMap =
      std::unordered_map<VkPhysicalDevice, PhysicalDeviceData>;
  using DeviceMap = std::unordered_map<VkDevice, DeviceData>;
  using QueueMap = std::unordered_map<VkQueue, QueueData>;

  ContextToken<InstanceMap> GetInstanceMap() {
    return ContextToken<InstanceMap>(instance_data_map_, instance_lock_);
  }

  ContextToken<InstanceData> GetInstanceData(VkInstance instance) {
    std::unique_lock<threading::mutex> locker(instance_lock_);
    return ContextToken<InstanceData>(instance_data_map_.at(instance),
                                      std::move(locker));
  }

  ContextToken<PhysicalDeviceMap> GetPhysicalDeviceMap() {
    return ContextToken<PhysicalDeviceMap>(physical_device_data_map_,
                                           physical_device_lock_);
  }

  ContextToken<PhysicalDeviceData> GetPhysicalDeviceData(
      VkPhysicalDevice physical_device) {
    std::unique_lock<threading::mutex> locker(physical_device_lock_);
    return ContextToken<PhysicalDeviceData>(
        physical_device_data_map_.at(physical_device), std::move(locker));
  }

  ContextToken<DeviceMap> GetDeviceMap() {
    return ContextToken<DeviceMap>(device_data_map_, device_lock_);
  }

  ContextToken<DeviceData> GetDeviceData(VkDevice device) {
    std::unique_lock<threading::mutex> locker(device_lock_);
    return ContextToken<DeviceData>(device_data_map_.at(device),
                                    std::move(locker));
  }

  ContextToken<QueueMap> GetQueueMap() {
    return ContextToken<QueueMap>(queue_data_map_, queue_lock_);
  }

  ContextToken<QueueData> GetQueueData(VkQueue queue) {
    std::unique_lock<threading::mutex> locker(queue_lock_);
    return ContextToken<QueueData>(queue_data_map_.at(queue),
                                   std::move(locker));
  }

 private:
  InstanceMap instance_data_map_;
  threading::mutex instance_lock_;

  PhysicalDeviceMap physical_device_data_map_;
  threading::mutex physical_device_lock_;

  DeviceMap device_data_map_;
  threading::mutex device_lock_;

  QueueMap queue_data_map_;
  threading::mutex queue_lock_;
};

Context &GetGlobalContext();

}  // namespace timing

#endif  // VK_TIMING_LAYER_H_
