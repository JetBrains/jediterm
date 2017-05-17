package com.jediterm.pty.process.monitor;

import java.util.HashMap;
import java.util.Map;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

/**
 * @author gaudima
 */


public class MacProcessMonitor  extends ProcessMonitor {
    private final int CTL_KERN = 1;
    private final int KERN_PROC = 14;
    private final int KERN_PROC_ALL = 0;
    private final int P_CONTROLT = 0x00002;

    private int mib_proc_all[] = {CTL_KERN, KERN_PROC, KERN_PROC_ALL};

    private static int kinfo_proc_size = 648;
    private static int kinfo_proc_pid_offset = 40;
    private static int kinfo_proc_ppid_offset = 560;
    private static int kinfo_proc_comm_offset = 243;
    private static int kinfo_proc_tpgid_offset = 576;
    private static int kinfo_proc_pgid_offset = 564;
    private static int kinfo_proc_flags_offset = 32;
    private static int kinfo_proc_starttime_offset = 8;

    private class ProcStat {
        private int pid = -1;
        private String comm = "";
        private int ppid = -1;
        private int pgid = -1;
        private int tpgid = -1;
        private long startTime = -1;
        private int flags;
        private ProcStat(Memory kInfoProcMem, long offset) {
            pid = kInfoProcMem.getInt(offset + kinfo_proc_pid_offset);
            ppid = kInfoProcMem.getInt(offset + kinfo_proc_ppid_offset);
            pgid = kInfoProcMem.getInt(offset + kinfo_proc_pgid_offset);
            tpgid = kInfoProcMem.getInt(offset + kinfo_proc_tpgid_offset);
            flags = kInfoProcMem.getInt(offset + kinfo_proc_flags_offset);
            startTime = kInfoProcMem.getLong(offset + kinfo_proc_starttime_offset);
            comm = kInfoProcMem.getString(offset + kinfo_proc_comm_offset);
        }
    }

    public interface CLib extends Library {
        int sysctl(int[] name, int namelen, Pointer oldp, IntByReference oldlenp, Pointer newp, int newlen);
    }

    private static CLib CLIB = (CLib) Native.loadLibrary("c", CLib.class);

    @Override
    protected Map<Integer, String> findJobNames() {
        Map<Integer, String> newJobNames = new HashMap<>();
        Map<Integer, Long> startTimesMax = new HashMap<>();
        IntByReference size = new IntByReference(-1);
        Memory m;

        while(true) {
            if (CLIB.sysctl(mib_proc_all, mib_proc_all.length, Pointer.NULL, size, Pointer.NULL, 0) < 0) {
                LOG.error("Can't get proceses info memory size");
                continue; // keep trying
            }
            m = new Memory(size.getValue());
            if (CLIB.sysctl(mib_proc_all, mib_proc_all.length, m, size, Pointer.NULL, 0) < 0) {
                LOG.error("Can't get proceses info");
                continue; // keep trying
            }
            break;
        }

        for(int offset = 0; offset < size.getValue(); offset += kinfo_proc_size) {
            ProcStat stat = new ProcStat(m, offset);
            long startTimeMax = 0;
            if (startTimesMax.containsKey(stat.ppid)) {
                startTimeMax = startTimesMax.get(stat.ppid);
            }
            if (pidsToWatch.containsKey(stat.ppid) &&
                    stat.pgid == stat.tpgid &&
                    (stat.flags & P_CONTROLT) > 0 &&
                    startTimeMax < stat.startTime) {
                startTimesMax.put(stat.ppid, stat.startTime);
                newJobNames.put(stat.ppid, stat.comm.substring(1, stat.comm.length() - 1));
            }
            if (!newJobNames.containsKey(stat.pid) && pidsToWatch.containsKey(stat.pid)) {
                newJobNames.put(stat.pid, stat.comm.substring(1, stat.comm.length() - 1));
            }
        }
        return newJobNames;
    }
}
