package com.jediterm.pty.process.monitor;


import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gaudima
 */

public class LinuxProcessMonitor extends ProcessMonitor {
    private class ProcfsStat {
        private int pid = -1;
        private String comm = "";
        private int ppid = -1;
        private int pgrp = -1;
        private int tpgid = -1;
        private long startTime = -1;
        private ProcfsStat(String stat) {
            if(stat != null) {
                String statArray[] = stat.split(" ");
                pid = Integer.parseInt(statArray[0]);
                comm = statArray[1];
                ppid = Integer.parseInt(statArray[3]);
                pgrp = Integer.parseInt(statArray[4]);
                tpgid = Integer.parseInt(statArray[7]);
                startTime = Long.parseLong(statArray[21]);
            }
        }
    }

    private Path proc = Paths.get("/proc");

    protected Map<Integer, String> findJobNames() {
        Map<Integer, String> newJobNames = new HashMap<>();
        Map<Integer, Long> startTimesMax = new HashMap<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(proc)) {
            for (Path path : dirStream) {
                if(Files.isDirectory(path) && path.getFileName().toString().matches("^\\d+$")) {
                    try (BufferedReader reader = Files.newBufferedReader(path.resolve("stat"))) {
                        ProcfsStat stat = new ProcfsStat(reader.readLine());

                        long startTimeMax = 0;
                        if (startTimesMax.containsKey(stat.ppid)) {
                            startTimeMax = startTimesMax.get(stat.ppid);
                        }
                        if (pidsToWatch.containsKey(stat.ppid) && stat.pgrp == stat.tpgid && startTimeMax < stat.startTime) {
                            startTimesMax.put(stat.ppid, stat.startTime);
                            newJobNames.put(stat.ppid, stat.comm.substring(1, stat.comm.length() - 1));
                        }
                        if (!newJobNames.containsKey(stat.pid) && pidsToWatch.containsKey(stat.pid)) {
                            newJobNames.put(stat.pid, stat.comm.substring(1, stat.comm.length() - 1));
                        }
                    } catch (IOException ex) {
                        LOG.debug("IOException reading from" + path + "/stat");
                    }
                }
            }
        } catch (IOException ex) {
            LOG.debug("IOException reading from /proc");
        }
        return newJobNames;
    }
}
