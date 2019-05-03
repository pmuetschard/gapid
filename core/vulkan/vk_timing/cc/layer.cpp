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

#include <cstring>
#include <map>

#include <vulkan/vk_layer.h>
#include <vulkan/vulkan.h>

#include "core/cc/log.h"

#include "layer.h"
#include "timing.h"

namespace timing {

Context &GetGlobalContext() {
  // We rely on C++11 static initialization rules here.
  // kContext will get allocated on first use, and freed in the
  // same order (more or less).
  static Context kContext;
  return kContext;
}

namespace {

template <typename T>
struct link_info_traits {
  const static bool is_instance =
      std::is_same<T, const VkInstanceCreateInfo>::value;
  using layer_info_type =
      typename std::conditional<is_instance, VkLayerInstanceCreateInfo,
                                VkLayerDeviceCreateInfo>::type;
  const static VkStructureType sType =
      is_instance ? VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO
                  : VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO;
};

template <typename T>
typename link_info_traits<T>::layer_info_type *get_layer_link_info(
    T *pCreateInfo) {
  using layer_info_type = typename link_info_traits<T>::layer_info_type;

  auto layer_info = const_cast<layer_info_type *>(
      static_cast<const layer_info_type *>(pCreateInfo->pNext));

  while (layer_info) {
    if (layer_info->sType == link_info_traits<T>::sType &&
        layer_info->function == VK_LAYER_LINK_INFO) {
      return layer_info;
    }
    layer_info = const_cast<layer_info_type *>(
        static_cast<const layer_info_type *>(layer_info->pNext));
  }
  return layer_info;
}

}  // namespace

VKAPI_ATTR VkResult VKAPI_CALL vkCreateInstance(
    const VkInstanceCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator, VkInstance *pInstance) {
  VkLayerInstanceCreateInfo *layer_info = get_layer_link_info(pCreateInfo);

  PFN_vkGetInstanceProcAddr get_instance_proc_addr =
      layer_info->u.pLayerInfo->pfnNextGetInstanceProcAddr;

  PFN_vkCreateInstance create_instance = reinterpret_cast<PFN_vkCreateInstance>(
      get_instance_proc_addr(NULL, "vkCreateInstance"));
  if (create_instance == NULL) {
    return VK_ERROR_INITIALIZATION_FAILED;
  }

  layer_info->u.pLayerInfo = layer_info->u.pLayerInfo->pNext;
  VkResult result = create_instance(pCreateInfo, pAllocator, pInstance);
  if (result != VK_SUCCESS) return result;

  InstanceData data;

#define GET_PROC(name) \
  data.name =          \
      reinterpret_cast<PFN_##name>(get_instance_proc_addr(*pInstance, #name))
  GET_PROC(vkGetInstanceProcAddr);
  GET_PROC(vkDestroyInstance);

  GET_PROC(vkEnumeratePhysicalDevices);
  GET_PROC(vkGetPhysicalDeviceProperties);
  GET_PROC(vkGetPhysicalDeviceQueueFamilyProperties);
#undef GET_PROC

  {
    auto instances = GetGlobalContext().GetInstanceMap();
    // The same instance was returned twice, this is a problem.
    if (instances->find(*pInstance) != instances->end()) {
      return VK_ERROR_INITIALIZATION_FAILED;
    }
    (*instances)[*pInstance] = data;
  }

  RegisterInstance(*pInstance, data);

  return VK_SUCCESS;
}

VKAPI_ATTR void vkDestroyInstance(VkInstance instance,
                                  const VkAllocationCallbacks *pAllocator) {
  auto instance_map = GetGlobalContext().GetInstanceMap();
  auto it = instance_map->find(instance);
  it->second.vkDestroyInstance(instance, pAllocator);
  instance_map->erase(it);
}

VKAPI_ATTR VkResult VKAPI_CALL
vkCreateDevice(VkPhysicalDevice gpu, const VkDeviceCreateInfo *pCreateInfo,
               const VkAllocationCallbacks *pAllocator, VkDevice *pDevice) {
  VkLayerDeviceCreateInfo *layer_info = get_layer_link_info(pCreateInfo);

  PFN_vkGetInstanceProcAddr get_instance_proc_addr =
      layer_info->u.pLayerInfo->pfnNextGetInstanceProcAddr;

  PFN_vkCreateDevice create_device = reinterpret_cast<PFN_vkCreateDevice>(
      get_instance_proc_addr(NULL, "vkCreateDevice"));
  if (!create_device) {
    return VK_ERROR_INITIALIZATION_FAILED;
  }

  PFN_vkGetDeviceProcAddr get_device_proc_addr =
      layer_info->u.pLayerInfo->pfnNextGetDeviceProcAddr;

  layer_info->u.pLayerInfo = layer_info->u.pLayerInfo->pNext;
  VkResult result = create_device(gpu, pCreateInfo, pAllocator, pDevice);
  if (result != VK_SUCCESS) return result;

  DeviceData data;
  data.instance = GetGlobalContext().GetPhysicalDeviceData(gpu)->instance;
  data.physicalDevice = gpu;

#define GET_PROC(name) \
  data.name =          \
      reinterpret_cast<PFN_##name>(get_device_proc_addr(*pDevice, #name));
  GET_PROC(vkGetDeviceProcAddr);
  GET_PROC(vkDestroyDevice);

  GET_PROC(vkGetDeviceQueue);
  GET_PROC(vkCreateQueryPool);
  GET_PROC(vkCreateCommandPool);
  GET_PROC(vkAllocateCommandBuffers);
  GET_PROC(vkFreeCommandBuffers);
  GET_PROC(vkCreateEvent);
  GET_PROC(vkResetEvent);
  GET_PROC(vkGetEventStatus);
  GET_PROC(vkSetEvent);
  GET_PROC(vkCreateFence);
  GET_PROC(vkWaitForFences);
  GET_PROC(vkDestroyFence);
  GET_PROC(vkQueueWaitIdle);
  GET_PROC(vkGetQueryPoolResults);
  GET_PROC(vkBeginCommandBuffer);
  GET_PROC(vkCmdResetQueryPool);
  GET_PROC(vkCmdSetEvent);
  GET_PROC(vkCmdWaitEvents);
  GET_PROC(vkCmdWriteTimestamp);
  GET_PROC(vkEndCommandBuffer);
  GET_PROC(vkQueueSubmit);
#undef GET_PROC

  {
    auto device_map = GetGlobalContext().GetDeviceMap();
    if (device_map->find(*pDevice) != device_map->end()) {
      return VK_ERROR_INITIALIZATION_FAILED;
    }
    (*device_map)[*pDevice] = data;
  }

  return VK_SUCCESS;
}

VKAPI_ATTR void vkDestroyDevice(VkDevice device,
                                const VkAllocationCallbacks *pAllocator) {
  auto device_map = GetGlobalContext().GetDeviceMap();
  auto it = device_map->find(device);
  it->second.vkDestroyDevice(device, pAllocator);
  device_map->erase(it);
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
vkGetInstanceProcAddr(VkInstance instance, const char *funcName) {
#define INTERCEPT(func)         \
  if (!strcmp(funcName, #func)) \
  return reinterpret_cast<PFN_vkVoidFunction>(func)

  INTERCEPT(vkGetInstanceProcAddr);
  INTERCEPT(vkCreateInstance);
  INTERCEPT(vkDestroyInstance);

  INTERCEPT(vkCreateDevice);
#undef INTERCEPT

  PFN_vkGetInstanceProcAddr instance_proc_addr =
      GetGlobalContext().GetInstanceData(instance)->vkGetInstanceProcAddr;
  return instance_proc_addr(instance, funcName);
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
vkGetDeviceProcAddr(VkDevice dev, const char *funcName) {
#define INTERCEPT(func)         \
  if (!strcmp(funcName, #func)) \
  return reinterpret_cast<PFN_vkVoidFunction>(func)

  INTERCEPT(vkGetDeviceProcAddr);
  INTERCEPT(vkDestroyDevice);

  INTERCEPT(vkGetDeviceQueue);
  INTERCEPT(vkQueueSubmit);
#undef INTERCEPT

  PFN_vkGetDeviceProcAddr device_proc_addr =
      GetGlobalContext().GetDeviceData(dev)->vkGetDeviceProcAddr;
  return device_proc_addr(dev, funcName);
}

}  // namespace timing

extern "C" {

VK_LAYER_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
TimingGetDeviceProcAddr(VkDevice dev, const char *funcName) {
  return timing::vkGetDeviceProcAddr(dev, funcName);
}

VK_LAYER_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
TimingGetInstanceProcAddr(VkInstance instance, const char *funcName) {
  return timing::vkGetInstanceProcAddr(instance, funcName);
}

static const VkLayerProperties global_layer_properties[] = {{
    "Timing",
    VK_VERSION_MAJOR(1) | VK_VERSION_MINOR(0) | 5,
    1,
    "command buffer timing",
}};

static VkResult get_layer_properties(uint32_t *pCount,
                                     VkLayerProperties *pProperties) {
  if (pProperties == NULL) {
    *pCount = 1;
    return VK_SUCCESS;
  }

  if (pCount == 0) {
    return VK_INCOMPLETE;
  }
  *pCount = 1;
  memcpy(pProperties, global_layer_properties, sizeof(global_layer_properties));
  return VK_SUCCESS;
}

VK_LAYER_EXPORT VKAPI_ATTR VkResult VKAPI_CALL
vkEnumerateInstanceLayerProperties(uint32_t *pCount,
                                   VkLayerProperties *pProperties) {
  return get_layer_properties(pCount, pProperties);
}

// On Android this must also be defined, even if we have 0
// layers to expose.
VK_LAYER_EXPORT VKAPI_ATTR VkResult VKAPI_CALL
vkEnumerateInstanceExtensionProperties(const char *pLayerName, uint32_t *pCount,
                                       VkExtensionProperties *pProperties) {
  *pCount = 0;
  return VK_SUCCESS;
}

VK_LAYER_EXPORT VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceLayerProperties(
    VkPhysicalDevice device, uint32_t *pCount, VkLayerProperties *pProperties) {
  return get_layer_properties(pCount, pProperties);
}

// On android this must also be defined, even if we have 0
// layers to expose.
VK_LAYER_EXPORT VKAPI_ATTR VkResult VKAPI_CALL
vkEnumerateDeviceExtensionProperties(VkPhysicalDevice device,
                                     const char *pLayerName, uint32_t *pCount,
                                     VkExtensionProperties *pProperties) {
  *pCount = 0;
  return VK_SUCCESS;
}

}  // extern "C"
