package com.jediterm.pty.process.monitor;

import com.jediterm.terminal.ui.UIUtil;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gaudima
 */

public class ProcessMonitor extends Thread {
    public interface TabNameChanger {
        void changeName(String name);
    }

    protected static final Logger LOG = Logger.getLogger(ProcessMonitor.class);

    protected HashMap<Integer, TabNameChanger> pidsToWatch = new HashMap<>();
    protected HashMap<Integer, String> jobNames = new HashMap<>();
    protected static final Object lock = new Object();
    private static ProcessMonitor instance = null;

    protected ProcessMonitor() {
        setName("ProcessMonitor");
        start();
    }

    protected Map<Integer, String> findJobNames()  {
        return null;
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized (lock) {
                    while (pidsToWatch.isEmpty()) {
                        lock.wait();
                    }
                    Map<Integer, String> newJobNames = findJobNames();
                    if(newJobNames == null) {
                        break;
                    }
                    for(Map.Entry<Integer, String> entry: newJobNames.entrySet()) {
                        int pid = entry.getKey();
                        String newName = entry.getValue();
                        String oldName = jobNames.get(pid);
                        if (!oldName.equals(newName)) {
                            pidsToWatch.get(pid).changeName(newName);
                            jobNames.put(pid, newName);
                        }
                    }
                    lock.wait(200);
                }
            } catch (InterruptedException ex) {
                break;
            }
        }
    }

    public static ProcessMonitor getInstance() {
        if(instance == null) {
            synchronized (lock) {
                if(instance == null) {
                    if (UIUtil.isLinux) {
                        instance = new LinuxProcessMonitor();
                    } else if (UIUtil.isWindows) {
                        instance = new WindowsProcessMonitor();
                    } else {
                        instance = new ProcessMonitor();
                    }
                }
            }
        }
        return instance;
    }

    public void watchPid(int pid, TabNameChanger changer) {
        if(pid >= 0) {
            synchronized (lock) {
                pidsToWatch.put(pid, changer);
                jobNames.put(pid, "Local");
                lock.notifyAll();
            }
        }
    }

    public void unwatchPid(int pid) {
        if(pid >= 0) {
            synchronized (lock) {
                pidsToWatch.remove(pid);
                jobNames.remove(pid);
                lock.notifyAll();
            }
        }
    }
}
