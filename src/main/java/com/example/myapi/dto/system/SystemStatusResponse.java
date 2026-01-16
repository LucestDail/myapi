package com.example.myapi.dto.system;

import java.time.Instant;
import java.util.Map;

/**
 * 서버 시스템 상태 응답
 */
public record SystemStatusResponse(
        // 서버 정보
        String hostname,
        String os,
        String arch,
        int availableProcessors,
        Instant timestamp,
        long uptimeMillis,
        
        // CPU
        double systemCpuLoad,
        double processCpuLoad,
        
        // 메모리 (bytes)
        long totalPhysicalMemory,
        long freePhysicalMemory,
        long usedPhysicalMemory,
        double memoryUsagePercent,
        
        // JVM Heap
        long heapMax,
        long heapUsed,
        long heapFree,
        double heapUsagePercent,
        
        // JVM Non-Heap
        long nonHeapUsed,
        
        // 디스크 (bytes)
        Map<String, DiskInfo> disks,
        
        // GC 정보
        long gcCount,
        long gcTime,
        
        // 스레드
        int threadCount,
        int peakThreadCount,
        
        // 클래스 로딩
        int loadedClassCount
) {
    public record DiskInfo(
            String path,
            long totalSpace,
            long freeSpace,
            long usedSpace,
            double usagePercent
    ) {}
}
