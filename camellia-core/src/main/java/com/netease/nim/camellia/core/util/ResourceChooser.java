package com.netease.nim.camellia.core.util;

import com.netease.nim.camellia.core.client.env.ProxyEnv;
import com.netease.nim.camellia.core.model.Resource;
import com.netease.nim.camellia.core.model.ResourceTable;
import com.netease.nim.camellia.core.model.operation.ResourceOperation;
import com.netease.nim.camellia.core.model.operation.ResourceReadOperation;
import com.netease.nim.camellia.core.model.operation.ResourceWriteOperation;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * Created by caojiajun on 2019/12/13.
 */
public class ResourceChooser {
    private final ResourceTable resourceTable;
    private final ProxyEnv proxyEnv;

    private CamelliaPair<Boolean, List<Resource>> readResources = null;
    private Resource readResource = null;
    private List<Resource> writeResources = null;

    private boolean bucketSizeIs2Power = false;

    private final Set<Resource> allResources;

    public ResourceChooser(ResourceTable resourceTable, ProxyEnv proxyEnv) {
        this.resourceTable = resourceTable;
        this.proxyEnv = proxyEnv;
        ResourceTable.Type type = resourceTable.getType();
        if (type == ResourceTable.Type.SHADING) {
            int bucketSize = resourceTable.getShadingTable().getBucketSize();
            bucketSizeIs2Power = MathUtil.is2Power(bucketSize);
        }
        this.allResources = ResourceUtil.getAllResources(resourceTable);
    }

    public ResourceTable.Type getType() {
        return resourceTable.getType();
    }

    public Set<Resource> getAllResources() {
        return allResources;
    }

    public Resource getReadResource(byte[]... shadingParam) {
        if (readResource != null) return readResource;
        ResourceTable.Type type = resourceTable.getType();
        if (type == ResourceTable.Type.SIMPLE) {
            CamelliaPair<Boolean, List<Resource>> readResources = _getReadResources(shadingParam);
            if (readResources == null) return null;
            List<Resource> list = readResources.getSecond();
            if (readResources.getFirst() && list.size() > 1) {
                int index = ThreadLocalRandom.current().nextInt(list.size());
                return list.get(index);
            } else {
                readResource = list.get(0);
                return readResource;
            }
        } else {
            CamelliaPair<Boolean, List<Resource>> readResources = _getReadResources(shadingParam);
            if (readResources == null) return null;
            List<Resource> list = readResources.getSecond();
            if (readResources.getFirst() && list.size() > 1) {
                int index = ThreadLocalRandom.current().nextInt(list.size());
                return list.get(index);
            } else {
                return list.get(0);
            }
        }
    }

    private CamelliaPair<Boolean, List<Resource>> _getReadResources(byte[]... shadingParam) {
        if (readResources != null) {
            return readResources;
        }
        ResourceTable.Type type = resourceTable.getType();
        if (type == ResourceTable.Type.SIMPLE) {
            ResourceTable.SimpleTable simpleTable = resourceTable.getSimpleTable();
            ResourceOperation resourceOperation = simpleTable.getResourceOperation();
            this.readResources = getReadResourcesFromOperation(resourceOperation);
            return this.readResources;
        } else {
            int shadingCode = proxyEnv.getShadingFunc().shadingCode(shadingParam);
            ResourceTable.ShadingTable shadingTable = resourceTable.getShadingTable();
            int bucketSize = shadingTable.getBucketSize();
            Map<Integer, ResourceOperation> operationMap = shadingTable.getResourceOperationMap();
            int index = MathUtil.mod(bucketSizeIs2Power, Math.abs(shadingCode), bucketSize);
            ResourceOperation resourceOperation = operationMap.get(index);
            return getReadResourcesFromOperation(resourceOperation);
        }
    }

    public List<Resource> getWriteResources(byte[]... shadingParam) {
        if (writeResources != null) return writeResources;
        ResourceTable.Type type = resourceTable.getType();
        if (type == ResourceTable.Type.SIMPLE) {
            ResourceTable.SimpleTable simpleTable = resourceTable.getSimpleTable();
            ResourceOperation resourceOperation = simpleTable.getResourceOperation();
            this.writeResources = getWriteResourcesFromOperation(resourceOperation);
            return this.writeResources;
        } else {
            int shadingCode = proxyEnv.getShadingFunc().shadingCode(shadingParam);
            ResourceTable.ShadingTable shadingTable = resourceTable.getShadingTable();
            int bucketSize = shadingTable.getBucketSize();
            Map<Integer, ResourceOperation> operationMap = shadingTable.getResourceOperationMap();
            int index = MathUtil.mod(bucketSizeIs2Power, Math.abs(shadingCode), bucketSize);
            ResourceOperation resourceOperation = operationMap.get(index);
            return getWriteResourcesFromOperation(resourceOperation);
        }
    }

    private CamelliaPair<Boolean, List<Resource>> getReadResourcesFromOperation(ResourceOperation resourceOperation) {
        ResourceOperation.Type resourceOperationType = resourceOperation.getType();
        if (resourceOperationType == ResourceOperation.Type.SIMPLE) {
            return new CamelliaPair<>(false, Collections.singletonList(resourceOperation.getResource()));
        } else if (resourceOperationType == ResourceOperation.Type.RW_SEPARATE) {
            ResourceReadOperation readOperation = resourceOperation.getReadOperation();
            ResourceReadOperation.Type readOperationType = readOperation.getType();
            if (readOperationType == ResourceReadOperation.Type.SIMPLE) {
                return new CamelliaPair<>(false, Collections.singletonList(readOperation.getReadResource()));
            } else if (readOperationType == ResourceReadOperation.Type.ORDER) {
                List<Resource> readResources = readOperation.getReadResources();
                return new CamelliaPair<>(false, readResources);
            } else if (readOperationType == ResourceReadOperation.Type.RANDOM) {
                List<Resource> readResources = readOperation.getReadResources();
                return new CamelliaPair<>(true, readResources);
            }
        }
        throw new IllegalArgumentException();
    }

    private List<Resource> getWriteResourcesFromOperation(ResourceOperation resourceOperation) {
        ResourceOperation.Type resourceOperationType = resourceOperation.getType();
        if (resourceOperationType == ResourceOperation.Type.SIMPLE) {
            return Collections.singletonList(resourceOperation.getResource());
        } else if (resourceOperationType == ResourceOperation.Type.RW_SEPARATE) {
            ResourceWriteOperation writeOperation = resourceOperation.getWriteOperation();
            ResourceWriteOperation.Type writeOperationType = writeOperation.getType();
            if (writeOperationType == ResourceWriteOperation.Type.SIMPLE) {
                return Collections.singletonList(writeOperation.getWriteResource());
            } else if (writeOperationType == ResourceWriteOperation.Type.MULTI) {
                List<Resource> writeResources = writeOperation.getWriteResources();
                return new ArrayList<>(writeResources);
            }
        }
        throw new IllegalArgumentException();
    }
}
