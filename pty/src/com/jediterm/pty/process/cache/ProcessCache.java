package com.jediterm.pty.process.cache;

import com.jediterm.terminal.ui.UIUtil;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gaudima
 */
public class ProcessCache extends Thread {
    public interface TabNameChanger {
        void changeName(String name);
    }

    protected static final Logger LOG = Logger.getLogger(ProcessCache.class);

    protected HashMap<Integer, TabNameChanger> pidsToWatch = new HashMap<>();
    protected HashMap<Integer, String> jobNames = new HashMap<>();
    private static ProcessCache instance = null;

    protected ProcessCache() {
        setName("ProcessCache");
        start();
    }

    protected Map<Integer, String> findJobNames()  {
        return new HashMap<>();
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized (ProcessCache.class) {
                    while (pidsToWatch.isEmpty()) {
                        ProcessCache.class.wait();
                    }
                    Map<Integer, String> newJobNames = findJobNames();
                    for(Map.Entry<Integer, String> entry: newJobNames.entrySet()) {
                        int pid = entry.getKey();
                        String newName = entry.getValue();
                        String oldName = jobNames.get(pid);
                        if (!oldName.equals(newName)) {
                            pidsToWatch.get(pid).changeName(newName);
                            jobNames.put(pid, newName);
                        }
                    }
                    ProcessCache.class.wait(200);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static ProcessCache getInstance() {
        if(instance == null) {
            if(UIUtil.isLinux) {
                instance = new LinuxProcessCache();
            } else if(UIUtil.isWindows) {
                instance = new WindowsProcessCache();
            } else {
                instance = new ProcessCache();
            }
        }
        return instance;
    }

    public void addPid(int pid, TabNameChanger changer) {
        if(pid >= 0) {
            synchronized (ProcessCache.class) {
                pidsToWatch.put(pid, changer);
                jobNames.put(pid, "Local");
                ProcessCache.class.notifyAll();
            }
        }
    }

    public void removePid(int pid) {
        if(pid >= 0) {
            synchronized (ProcessCache.class) {
                pidsToWatch.remove(pid);
                jobNames.remove(pid);
                ProcessCache.class.notifyAll();
            }
        }
    }
}
