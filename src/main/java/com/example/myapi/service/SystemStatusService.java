package com.example.myapi.service;

import com.example.myapi.dto.system.SystemStatusResponse;
import com.example.myapi.dto.system.SystemStatusResponse.DiskInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 서버 시스템 상태 모니터링 서비스
 */
@Service
public class SystemStatusService {

    private final Runtime runtime = Runtime.getRuntime();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

    public SystemStatusResponse getSystemStatus() {
        // 호스트명
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }

        // OS 정보
        String os = osMXBean.getName() + " " + osMXBean.getVersion();
        String arch = osMXBean.getArch();
        int processors = osMXBean.getAvailableProcessors();

        // CPU 로드 (com.sun.management 사용)
        double systemCpuLoad = -1;
        double processCpuLoad = -1;
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean sunOsMXBean) {
            systemCpuLoad = sunOsMXBean.getCpuLoad() * 100;
            processCpuLoad = sunOsMXBean.getProcessCpuLoad() * 100;
        }

        // 물리 메모리
        long totalPhysicalMemory = 0;
        long freePhysicalMemory = 0;
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean sunOsMXBean) {
            totalPhysicalMemory = sunOsMXBean.getTotalMemorySize();
            freePhysicalMemory = sunOsMXBean.getFreeMemorySize();
        }
        long usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory;
        double memoryUsagePercent = totalPhysicalMemory > 0 
                ? (double) usedPhysicalMemory / totalPhysicalMemory * 100 : 0;

        // JVM Heap
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long heapMax = heapUsage.getMax();
        long heapUsed = heapUsage.getUsed();
        long heapFree = heapMax - heapUsed;
        double heapUsagePercent = heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;

        // JVM Non-Heap
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeapUsage.getUsed();

        // 디스크 정보
        Map<String, DiskInfo> disks = new HashMap<>();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            long used = total - free;
            double usagePercent = total > 0 ? (double) used / total * 100 : 0;
            disks.put(root.getAbsolutePath(), new DiskInfo(
                    root.getAbsolutePath(),
                    total,
                    free,
                    used,
                    usagePercent
            ));
        }

        // GC 정보
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount();
            gcTime += gc.getCollectionTime();
        }

        // 스레드
        int threadCount = threadMXBean.getThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();

        // 클래스 로딩
        int loadedClassCount = classLoadingMXBean.getLoadedClassCount();

        // Uptime
        long uptimeMillis = runtimeMXBean.getUptime();

        return new SystemStatusResponse(
                hostname,
                os,
                arch,
                processors,
                Instant.now(),
                uptimeMillis,
                systemCpuLoad,
                processCpuLoad,
                totalPhysicalMemory,
                freePhysicalMemory,
                usedPhysicalMemory,
                memoryUsagePercent,
                heapMax,
                heapUsed,
                heapFree,
                heapUsagePercent,
                nonHeapUsed,
                disks,
                gcCount,
                gcTime,
                threadCount,
                peakThreadCount,
                loadedClassCount
        );
    }
}
