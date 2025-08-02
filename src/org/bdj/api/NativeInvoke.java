package org.bdj.api;

import org.bdj.Status;
import org.bdj.api.API;
import org.bdj.api.Buffer;

public class NativeInvoke {
    static API api;
    static long sceKernelSendNotificationRequestAddr;

    static {
        try {
            api = API.getInstance();
            sceKernelSendNotificationRequestAddr = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sceKernelSendNotificationRequest");
            
            // Debug: Check if function was found
            Status.println("sceKernelSendNotificationRequest address: 0x" + Long.toHexString(sceKernelSendNotificationRequestAddr));
            
        } catch (Exception e) {
            Status.printStackTrace("Error in NativeInvoke: ", e);
        }
    }

    public static int sendNotificationRequest(String msg) {
        Status.println("sendNotificationRequest test: " + msg);
        
        // Check if function address was resolved
        if (sceKernelSendNotificationRequestAddr == 0) {
            Status.println("ERROR: sceKernelSendNotificationRequest function not found!");
            return -1;
        }
        
        long size = 0xc30;
        Buffer buffer = new Buffer((int)size);

        try {
            // Initialize buffer to zero
            buffer.fill((byte)0);
            
            // Set specific values in the buffer structure
            buffer.putInt(0x10, -1);
            
            // Put message string at offset 0x2d - use byte-by-byte copy like original
            byte[] msgBytes = msg.getBytes();
            for (int i = 0; i < msgBytes.length && i < (size - 0x2d - 1); i++) {
                buffer.putByte(0x2d + i, msgBytes[i]);
            }
            // Ensure null termination
            buffer.putByte(0x2d + Math.min(msgBytes.length, (int)(size - 0x2d - 1)), (byte)0);
            
            Status.println("Calling native function at 0x" + Long.toHexString(sceKernelSendNotificationRequestAddr));
            Status.println("Buffer address: 0x" + Long.toHexString(buffer.address()));
            Status.println("Buffer size: 0x" + Long.toHexString(size));
            
            long res = api.call(sceKernelSendNotificationRequestAddr, 0, buffer.address(), size, 0);
            
            Status.println("Function returned: " + res + " (0x" + Long.toHexString(res) + ")");
            
            return (int)res;
            
        } finally {
            // Buffer will be automatically freed by finalizer, but we can be explicit
            // Note: In the original code, memory was freed immediately
        }
    }
}