package com.jediterm.terminal.process.cache;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

/**
 * @author gaudima
 */
public class WindowsProcessCache extends ProcessCache {

    @Override
    protected String findJobName(int pid) {
        Kernel32 kern = (Kernel32) Native.loadLibrary(Kernel32.class, W32APIOptions.UNICODE_OPTIONS);
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE snapshot = kern.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        String jobName = "";
        String shellName = "";
        while(kern.Process32Next(snapshot, processEntry)) {
            if(processEntry.th32ParentProcessID.intValue() == pid) {
                jobName = Native.toString(processEntry.szExeFile);
            }
            if(processEntry.th32ProcessID.intValue() == pid) {
                shellName = Native.toString(processEntry.szExeFile);
            }
        }
        if(jobName.isEmpty()) {
            jobName = shellName;
        }
        if(jobName.endsWith(".exe")) {
            jobName = jobName.substring(0, jobName.length() - 4);
        }
        return jobName;
    }
}
