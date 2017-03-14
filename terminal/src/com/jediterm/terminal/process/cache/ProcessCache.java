package com.jediterm.terminal.process.cache;

import com.jediterm.terminal.ui.UIUtil;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author gaudima
 */
public class ProcessCache extends Thread {
    public interface TabNameChanger {
        void changeName(String name);
    }
    protected static final Logger LOG = Logger.getLogger(ProcessCache.class);

    protected Map<Integer, TabNameChanger> pidsToWatch = new ConcurrentHashMap<>();
    protected Map<Integer, String> jobNames = new ConcurrentHashMap<>();
    private static ProcessCache instance = null;
    protected ProcessCache() {
        setName("ProcessCache");
        start();
    }

    protected String findJobName(int pid)  {
        return "Local";
    }

    @Override
    public void run() {
        while (true) {
            for (Map.Entry<Integer, TabNameChanger> entry: pidsToWatch.entrySet()) {
                String jobName = findJobName(entry.getKey());
                if(!jobName.equals(jobNames.get(entry.getKey()))) {
                    entry.getValue().changeName(jobName);
                    jobNames.put(entry.getKey(), jobName);
                }
            }
            try {
                sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static ProcessCache getInstance() {
        if(instance == null) {
            if(UIUtil.isLinux) {
                instance = new LinuxProcessCache();
            } else {
                instance = new ProcessCache();
            }
        }
        return instance;
    }

    public void addPid(int pid, TabNameChanger changer) {
        if(pid != -1) {
            pidsToWatch.put(pid, changer);
            jobNames.put(pid, "Local");
        }
    }

    public void removePid(int pid) {
        if(pid != -1) {
            pidsToWatch.remove(pid);
            jobNames.remove(pid);
        }
    }
}
