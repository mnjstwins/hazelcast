/*
 * Copyright (c) 2008-2012, Hazel Bilisim Ltd. All Rights Reserved.
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

package com.hazelcast.impl.spi;

import com.hazelcast.nio.Data;
import com.hazelcast.util.ResponseQueueFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static com.hazelcast.nio.IOUtil.toObject;

class PartitionIterator extends AbstractOperation {
    ArrayList<Integer> partitions;
    Data operationData;

    public PartitionIterator(ArrayList<Integer> partitions, Data operationData) {
        this.partitions = partitions;
        this.operationData = operationData;
    }

    public PartitionIterator() {
    }

    public void run() {
        try {
            final NodeService nodeService = getNodeService();
            Map<Integer, Object> results = new HashMap<Integer, Object>(partitions.size());
            Map<Integer, ResponseQueue> responses = new HashMap<Integer, ResponseQueue>(partitions.size());
            for (final int partitionId : partitions) {
                Operation op = (Operation) toObject(operationData);
                ResponseQueue r = new ResponseQueue();
                op.setNodeService(getNodeService())
                        .setCaller(getCaller())
                        .setPartitionId(partitionId)
                        .setResponseHandler(r)
                        .setService(getService());
                nodeService.runLocally(op);
                responses.put(partitionId, r);
            }
            for (Map.Entry<Integer, ResponseQueue> partitionResponse : responses.entrySet()) {
                Object result = partitionResponse.getValue().get();
                results.put(partitionResponse.getKey(), result);
            }
            getResponseHandler().sendResponse(results);
        } catch (Exception e) {
            e.printStackTrace();
            getResponseHandler().sendResponse(e);
        }
    }

    class ResponseQueue implements ResponseHandler {
        final BlockingQueue b = ResponseQueueFactory.newResponseQueue();

        public void sendResponse(Object obj) {
            b.offer(obj);
        }

        public Object get() throws InterruptedException {
            return b.take();
        }
    }

    @Override
    public void writeInternal(DataOutput out) throws IOException {
        super.writeInternal(out);
        int pCount = partitions.size();
        out.writeInt(pCount);
        for (int i = 0; i < pCount; i++) {
            out.writeInt(partitions.get(i));
        }
        operationData.writeData(out);
    }

    @Override
    public void readInternal(DataInput in) throws IOException {
        super.readInternal(in);
        int pCount = in.readInt();
        partitions = new ArrayList<Integer>(pCount);
        for (int i = 0; i < pCount; i++) {
            partitions.add(in.readInt());
        }
        operationData = new Data();
        operationData.readData(in);
    }
}
