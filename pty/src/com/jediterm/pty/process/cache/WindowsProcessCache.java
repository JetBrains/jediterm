package com.jediterm.pty.process.cache;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gaudima
 */
public class WindowsProcessCache extends ProcessCache {

    @Override
    protected Map<Integer, String> findJobNames() {
        Kernel32 kern = (Kernel32) Native.loadLibrary(Kernel32.class, W32APIOptions.UNICODE_OPTIONS);
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE snapshot = kern.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        Map<Integer, String> newJobNames = new HashMap<>();
        while(kern.Process32Next(snapshot, processEntry)) {
            int pid = processEntry.th32ParentProcessID.intValue();
            String name = "";
            if(pidsToWatch.containsKey(pid)) {
                name = Native.toString(processEntry.szExeFile);
            }
            if(!newJobNames.containsKey(processEntry.th32ProcessID.intValue()) && pidsToWatch.containsKey(pid)) {
                pid = processEntry.th32ProcessID.intValue();
                name = Native.toString(processEntry.szExeFile);
            }
            if(name.endsWith(".exe")) {
                name = name.substring(0, name.length() - 4);
            }
            newJobNames.put(pid, name);
        }
        return newJobNames;
    }
}
