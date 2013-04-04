/**
   Copyright [2013] [Mushroom]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
/**
 Notice : this source is extracted from Hadoop metric2 package
 and some source code may changed by zavakid
 */
package com.zavakid.mushroom.impl;

import java.util.ConcurrentModificationException;

/**
 * A half-blocking (nonblocking for producers, blocking for consumers) queue for metrics sinks.
 * <p>
 * New elements are dropped when the queue is full to preserve "interesting" elements at the onset of queue filling
 * events
 * 
 * @author Hadoop metric2 package's authors
 * @author zavakid 2013 2013-4-4 下午8:20:19
 * @since 0.1
 */
public class SinkQueue<T> {

    // A fixed size circular buffer to minimize garbage
    private final T[] data;
    private int       head;                  // head position
    private int       tail;                  // tail position
    private int       size;                  // number of elements
    private Thread    currentConsumer = null;

    SinkQueue(int capacity){
        this.data = (T[]) new Object[Math.max(1, capacity)];
        head = tail = size = 0;
    }

    synchronized boolean enqueue(T e) {
        if (data.length == size) {
            return false;
        }
        ++size;
        tail = (tail + 1) % data.length;
        data[tail] = e;
        notify();
        return true;
    }

    /**
     * Consume one element, will block if queue is empty Only one consumer at a time is allowed
     * 
     * @param consumer the consumer callback object
     */
    void consume(Consumer<T> consumer) throws InterruptedException {
        T e = waitForData();

        try {
            consumer.consume(e); // can take forever
            doDequeue();
        } finally {
            clearConsumer();
        }
    }

    /**
     * Consume all the elements, will block if queue is empty
     * 
     * @param consumer the consumer callback object
     * @throws InterruptedException
     */
    void consumeAll(Consumer<T> consumer) throws InterruptedException {
        waitForData();

        try {
            for (int i = size(); i-- > 0;) {
                consumer.consume(front()); // can take forever
                doDequeue();
            }
        } finally {
            clearConsumer();
        }
    }

    /**
     * Dequeue one element from head of the queue, will block if queue is empty
     * 
     * @return the first element
     * @throws InterruptedException
     */
    synchronized T dequeue() throws InterruptedException {
        checkConsumer();

        while (0 == size) {
            wait();
        }
        return doDequeue();
    }

    private synchronized T waitForData() throws InterruptedException {
        checkConsumer();

        while (0 == size) {
            wait();
        }
        currentConsumer = Thread.currentThread();
        return front();
    }

    private synchronized void checkConsumer() {
        if (currentConsumer != null) {
            throw new ConcurrentModificationException("The " + currentConsumer.getName()
                                                      + " thread is consuming the queue.");
        }
    }

    private synchronized void clearConsumer() {
        currentConsumer = null;
    }

    private synchronized T doDequeue() {
        if (0 == size) {
            throw new IllegalStateException("Size must > 0 here.");
        }
        --size;
        head = (head + 1) % data.length;
        T ret = data[head];
        data[head] = null; // hint to gc
        return ret;
    }

    synchronized T front() {
        return data[(head + 1) % data.length];
    }

    synchronized T back() {
        return data[tail];
    }

    synchronized void clear() {
        checkConsumer();

        for (int i = data.length; i-- > 0;) {
            data[i] = null;
        }
        size = 0;
    }

    synchronized int size() {
        return size;
    }

    int capacity() {
        return data.length;
    }
}
