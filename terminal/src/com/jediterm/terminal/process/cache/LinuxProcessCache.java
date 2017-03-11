package com.jediterm.terminal.process.cache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.*;

/**
 * @author gaudima
 */

public class LinuxProcessCache extends ProcessCache {
    private File proc = new File("/proc");

    protected LinuxProcessCache() {
        super();
    }

    protected String findJobName(int pid) {
        String allPids[] = proc.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return new File(file, s).isDirectory() && s.matches("^\\d+$");
            }
        });
        String jobName = "";
        long startTimeMax = 0;
        for (String cpid : allPids) {
            try {
                Scanner scan = new Scanner(new File(proc, cpid + "/stat"));
                scan.next();
                String name = scan.next();
                scan.next();
                int ppid = scan.nextInt();
                for (int i = 4; i < 7; i++) {
                    scan.next();
                }
                int tpgid = scan.nextInt();
                for (int i = 8; i < 21; i++) {
                    scan.next();
                }
                long startTime = scan.nextLong();
                scan.close();
                if (ppid == pid && tpgid != pid && startTimeMax < startTime) {
                    startTimeMax = startTime;
                    jobName = name;
                }
            } catch (FileNotFoundException ex) {
                LOG.debug("Process " + pid + " exited earlier");
            }
        }
        if (jobName.isEmpty()) {
            try {
                Scanner scan = new Scanner(new File(proc, Integer.toString(pid) + "/comm"));
                jobName = scan.next();
                scan.close();
            } catch (FileNotFoundException ex) {
                // This should never happen
                LOG.debug("Shell with pid: " + pid + " already exited.");
            }
        } else {
            jobName = jobName.substring(1, jobName.length() - 1);
        }
        return jobName;
    }
}
