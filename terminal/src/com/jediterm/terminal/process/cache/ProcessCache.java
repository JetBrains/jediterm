package com.jediterm.terminal.process.cache;

import com.jediterm.terminal.ui.UIUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by gaudima on 3/11/17.
 */
public class ProcessCache extends Thread {
    public interface TabNameChanger {
        void changeName(String name);
    }

    protected Map<Integer, TabNameChanger> pidsToWatch = new ConcurrentHashMap<>();
    protected Map<Integer, String> jobNames = new ConcurrentHashMap<>();
    private static ProcessCache instance = null;
    protected ProcessCache() {
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
        pidsToWatch.put(pid, changer);
        jobNames.put(pid, "Local");
    }

    public void removePid(int pid) {
        pidsToWatch.remove(pid);
        jobNames.remove(pid);
    }
}
