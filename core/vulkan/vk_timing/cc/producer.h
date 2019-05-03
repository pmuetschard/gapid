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

#ifndef VK_TIMING_PRODUCER_H_
#define VK_TIMING_PRODUCER_H_

namespace timing {

void send_event(int32_t pid, uint64_t queue_id, uint32_t queue_idx, uint64_t start_ts, uint64_t end_ts, const char* label);

}  // namespace timing

#endif  // VK_TIMING_PRODUCER_H_
