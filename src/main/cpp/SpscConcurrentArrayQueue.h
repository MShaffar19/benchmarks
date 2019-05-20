/*
 * Copyright 2014-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef SPSC_CONCURRENT_ARRAY_QUEUE_H
#define SPSC_CONCURRENT_ARRAY_QUEUE_H

#include <cstdint>
#include <array>
#include <atomic>

#include "util/BitUtil.h"

template<typename T>
class SpscConcurrentArrayQueue
{
public:
    SpscConcurrentArrayQueue(std::int32_t requestedCapacity) :
        m_head(0), m_tail(0), m_capacity(aeron::util::BitUtil::findNextPowerOfTwo(requestedCapacity))
    {
        m_mask = m_capacity - 1;
        m_buffer = new std::atomic<T*>[m_capacity];

        for (int i = 0; i < m_capacity - 1; i++)
        {
            m_buffer[i].store(nullptr, std::memory_order_relaxed);
        }

        m_buffer[m_capacity - 1].store(nullptr, std::memory_order_acq_rel);
    }

    ~SpscConcurrentArrayQueue()
    {
        delete[] m_buffer;
    }

    inline bool offer(T* t)
    {
        if (nullptr == t)
        {
            return false;
        }

        std::int64_t currentTail =  m_tail.load(std::memory_order_seq_cst);
        int index = (int) (currentTail & m_mask);

        std::atomic<T*>* source = &m_buffer[index];
        T* ptr = source->load(std::memory_order_seq_cst);
        if (nullptr == ptr)
        {
            source->store(t, std::memory_order_acq_rel);
            m_tail.store(currentTail + 1, std::memory_order_acq_rel);

            return true;
        }

        return false;
    }

    inline T* poll()
    {
        std::int64_t currentHead = m_head.load(std::memory_order_seq_cst);
        int index = (int) (currentHead & m_mask);
        std::atomic<T*>* source = &m_buffer[index];
        T* t = source->load(std::memory_order_seq_cst);

        if (nullptr != t)
        {
            source->store(nullptr, std::memory_order_acq_rel);
            m_head.store(currentHead + 1, std::memory_order_acq_rel);
        }

        return const_cast<T*>(t);
    }

private:
    std::atomic<T*>* m_buffer;
    std::atomic<std::int64_t> m_head;
    std::atomic<std::int64_t> m_tail;
    std::int64_t m_mask;
    std::int32_t m_capacity;
};

#endif //SPSC_CONCURRENT_ARRAY_QUEUE_H
