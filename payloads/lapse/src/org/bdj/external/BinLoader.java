package org.bdj.external;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.bdj.Status;
import org.bdj.api.*;

public class BinLoader {
    // File io
    private static final int O_RDONLY = 0x0;
    private static final int O_WRONLY = 0x1;
    private static final int O_RDWR = 0x2;
    private static final int O_CREAT = 0x200;
    private static final int O_TRUNC = 0x400;
    private static final int O_NONBLOCK = 0x4;

    private static final int ENOENT = 2;   // No such file or directory
    private static final int EACCES = 13;  // Permission denied
    private static final int EISDIR = 21;  // Is a directory

    // Memory mapping constants
    private static final int PROT_READ = 0x1;
    private static final int PROT_WRITE = 0x2;
    private static final int PROT_EXEC = 0x4;
    private static final int MAP_PRIVATE = 0x2;
    private static final int MAP_ANONYMOUS = 0x1000;
    
    // ELF constants
    private static final int ELF_MAGIC = 0x464c457f; // 0x7F 'E' 'L' 'F' in little endian
    private static final int PT_LOAD = 1;
    private static final int PAGE_SIZE = 0x1000;
    private static final int MAX_PAYLOAD_SIZE = 4 * 1024 * 1024; // 4MB
    
    // Network constants
    private static final int NETWORK_PORT = 9020;
    private static final int READ_CHUNK_SIZE = 4096;
    private static final int COPY_CHUNK_SIZE = 8192;
    
    // File paths
    private static final String USB_PAYLOAD_PATH = "/mnt/usb0/payload.bin";
    private static final String DATA_PAYLOAD_PATH = "/data/payload.bin";
    
    private static API api;
    private static byte[] binData;
    private static long mmapBase;
    private static long mmapSize;
    private static long entryPoint;
    private static Thread payloadThread;
    private static Thread binLoaderThread;
    private static boolean isRunning = false;

    static {
        try {
            api = API.getInstance();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void start() {
        if (isRunning) {
            Status.println("BinLoader is already running");
            return;
        }
        
        Status.println("Starting BinLoader in background thread...");
        
        binLoaderThread = new Thread(new Runnable() {
            public void run() {
                try {
                    isRunning = true;
                    runBinLoader();
                } catch (Exception e) {
                    Status.println("BinLoader thread error: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    isRunning = false;
                }
            }
        });
        
        binLoaderThread.setName("BinLoaderThread");
        binLoaderThread.start();
        
        Status.println("BinLoader thread started successfully");
    }
    
    private static void runBinLoader() {
        Status.println("=== BinLoader Starting ===");
        
        // Priority 1: Check for USB payload and copy to data
        if (fileExists(USB_PAYLOAD_PATH)) {
            Status.println("Found USB payload, copying to data directory...");
            
            try {
                copyFile(USB_PAYLOAD_PATH, DATA_PAYLOAD_PATH);
                Status.println("Successfully copied payload to: " + DATA_PAYLOAD_PATH);
                
                // Execute from data location
                executePayloadFromPath(DATA_PAYLOAD_PATH);
                return;
                
            } catch (Exception e) {
                Status.println("Failed to copy USB payload: " + e.getMessage());
                Status.println("Attempting to execute directly from USB...");
                
                try {
                    executePayloadFromPath(USB_PAYLOAD_PATH);
                    return;
                } catch (Exception e2) {
                    Status.println("Failed to execute from USB: " + e2.getMessage());
                }
            }
        }
        
        // Priority 2: Check for existing payload in data directory
        if (fileExists(DATA_PAYLOAD_PATH)) {
            Status.println("Found existing payload in data directory, executing...");
            executePayloadFromPath(DATA_PAYLOAD_PATH);
            return;
        }
        
        // Priority 3: Start network server
        Status.println("No payload files found, starting network server...");
        listenForPayloadOnPort(NETWORK_PORT);
    }
    

    private static void copyFile(String sourcePath, String destPath) throws Exception {
        Text sourceText = new Text(sourcePath);
        Text destText = new Text(destPath);

        long sourceFd = Helper.syscall(Helper.SYS_OPEN, sourceText.address(), (long)O_RDONLY, 0L);
        if (sourceFd < 0) {
            throw new RuntimeException("Failed to open source file: " + sourcePath + " (fd: " + sourceFd + ")");
        }
        
        try {
            long destFd = Helper.syscall(Helper.SYS_OPEN, destText.address(), 
                                       (long)(O_WRONLY | O_CREAT | O_TRUNC), 0644L);
            if (destFd < 0) {
                throw new RuntimeException("Failed to create destination file: " + destPath + " (fd: " + destFd + ")");
            }
            
            try {
                Buffer copyBuffer = new Buffer(COPY_CHUNK_SIZE);
                long totalBytesCopied = 0;
                
                while (true) {
                    long bytesRead = Helper.syscall(Helper.SYS_READ, sourceFd, copyBuffer.address(), (long)COPY_CHUNK_SIZE);
                    
                    if (bytesRead < 0) {
                        throw new RuntimeException("Read error during copy: " + bytesRead);
                    }
                    
                    if (bytesRead == 0) {
                        // EOF reached
                        break;
                    }
                    
                    long bytesWritten = Helper.syscall(Helper.SYS_WRITE, destFd, copyBuffer.address(), bytesRead);
                    if (bytesWritten != bytesRead) {
                        throw new RuntimeException("Write error during copy: expected " + bytesRead + ", wrote " + bytesWritten);
                    }
                    
                    totalBytesCopied += bytesWritten;
                    
                    if (totalBytesCopied > MAX_PAYLOAD_SIZE) {
                        throw new RuntimeException("File too large during copy: " + totalBytesCopied + " bytes");
                    }
                }
                
                Status.println("Successfully copied " + totalBytesCopied + " bytes from " + sourcePath + " to " + destPath);
                
            } finally {
                long closeResult = Helper.syscall(Helper.SYS_CLOSE, destFd);
                if (closeResult < 0) {
                    Status.println("Warning: Failed to close destination file: " + closeResult);
                }
            }
            
        } finally {
            long closeResult = Helper.syscall(Helper.SYS_CLOSE, sourceFd);
            if (closeResult < 0) {
                Status.println("Warning: Failed to close source file: " + closeResult);
            }
        }
    }


    public static void loadFromFile(String filePath) throws Exception {
        byte[] data = readFile(filePath);
        Status.println("Loaded payload from: " + filePath + " (size: " + data.length + " bytes)");
        
        loadFromData(data);
    }

    public static void loadFromData(byte[] data) throws Exception {
        binData = data;
        
        // Round up to page boundary
        long mmapSizeCalc = roundUp(data.length, PAGE_SIZE);
        
        // Allocate executable memory
        int protFlags = PROT_READ | PROT_WRITE | PROT_EXEC;
        int mapFlags = MAP_PRIVATE | MAP_ANONYMOUS;
        
        long ret = Helper.syscall(Helper.SYS_MMAP, 0L, mmapSizeCalc, (long)protFlags, (long)mapFlags, -1L, 0L);
        if (ret < 0) {
            throw new RuntimeException("mmap() failed with error: " + ret);
        }
        
        mmapBase = ret;
        mmapSize = mmapSizeCalc;
        
        Status.println("mmap() allocated at: 0x" + Long.toHexString(mmapBase));
        
        // Check if ELF by reading magic bytes
        if (data.length >= 4) {
            int magic = ((data[3] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) | 
                       ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
            
            if (magic == ELF_MAGIC) {
                Status.println("Detected ELF payload, parsing headers...");
                entryPoint = loadElfSegments(data);
            } else {
                Status.println("Non-ELF payload, treating as raw shellcode");
                // Copy raw data to allocated memory
                api.memcpy(mmapBase, data, data.length);
                entryPoint = mmapBase;
            }
        } else {
            throw new RuntimeException("Payload too small");
        }
        
        Status.println("Entry point: 0x" + Long.toHexString(entryPoint));
    }
    
    private static long loadElfSegments(byte[] data) throws Exception {
        // Copy data to allocated memory first for easier parsing
        api.memcpy(mmapBase, data, data.length);
        
        // Read ELF header
        ElfHeader elfHeader = readElfHeader(mmapBase);
        
        // Load program segments
        for (int i = 0; i < elfHeader.phNum; i++) {
            long phdrAddr = mmapBase + elfHeader.phOff + (i * elfHeader.phEntSize);
            ProgramHeader phdr = readProgramHeader(phdrAddr);
            
            if (phdr.type == PT_LOAD && phdr.memSize > 0) {
                // Calculate segment address (use relative offset)
                long segAddr = mmapBase + (phdr.vAddr % 0x1000000);
                
                // Copy segment data
                if (phdr.fileSize > 0) {
                    api.memcpy(segAddr, mmapBase + phdr.offset, phdr.fileSize);
                }
                
                // Zero out remaining memory if memSize > fileSize
                if (phdr.memSize > phdr.fileSize) {
                    api.memset(segAddr + phdr.fileSize, 0, phdr.memSize - phdr.fileSize);
                }
            }
        }
        
        // Return entry point (relative to base)
        return mmapBase + (elfHeader.entry % 0x1000000);
    }
    
    public static void run() throws Exception {
        Status.println("Spawning payload in new thread...");
        
        // Create Java thread to execute the payload
        payloadThread = new Thread(new Runnable() {
            public void run() {
                try {
                    Status.println("Executing payload at entry point: 0x" + Long.toHexString(entryPoint));
                    
                    // Call the entry point function
                    long result = api.call(entryPoint);
                    
                    Status.println("Payload execution completed with result: " + result);
                } catch (Exception e) {
                    Status.println("Payload execution error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        
        payloadThread.setName("PayloadThread");
        payloadThread.start();
        
        Status.println("Payload thread started successfully");
    }
    

    public static void waitForPayloadToExit() throws Exception {
        if (payloadThread != null) {
            Status.println("Waiting for payload thread to complete...");
            try {
                payloadThread.join(); // Wait for thread to finish
                Status.println("Payload thread completed");
            } catch (InterruptedException e) {
                Status.println("Thread wait interrupted: " + e.getMessage());
            }
        }
        
        // Cleanup allocated memory
        Status.println("Cleaning up payload memory...");
        long ret = Helper.syscall(Helper.SYS_MUNMAP, mmapBase, mmapSize);
        if (ret < 0) {
            Status.println("Warning: munmap() failed: " + ret);
        }
        
        Status.println("Payload execution completed and cleaned up");
    }
    

    private static class ElfHeader {
        long entry;
        long phOff;
        int phEntSize;
        int phNum;
    }
    
    private static class ProgramHeader {
        int type;
        long offset;
        long vAddr;
        long fileSize;
        long memSize;
    }
    

    private static ElfHeader readElfHeader(long addr) {
        ElfHeader header = new ElfHeader();
        header.entry = api.read64(addr + 0x18);
        header.phOff = api.read64(addr + 0x20);
        header.phEntSize = api.read16(addr + 0x36) & 0xFFFF;
        header.phNum = api.read16(addr + 0x38) & 0xFFFF;
        return header;
    }
    

    private static ProgramHeader readProgramHeader(long addr) {
        ProgramHeader phdr = new ProgramHeader();
        phdr.type = api.read32(addr + 0x00);
        phdr.offset = api.read64(addr + 0x08);
        phdr.vAddr = api.read64(addr + 0x10);
        phdr.fileSize = api.read64(addr + 0x20);
        phdr.memSize = api.read64(addr + 0x28);
        return phdr;
    }
    
    private static long roundUp(long value, long boundary) {
        return ((value + boundary - 1) / boundary) * boundary;
    }
    
    private static byte[] readFile(String filePath) throws Exception {
        Text pathText = new Text(filePath);
        
        // Open file for reading
        long fd = Helper.syscall(Helper.SYS_OPEN, pathText.address(), (long)O_RDONLY, 0L);
        if (fd < 0) {
            int errno = api.errno();
            String errorMsg = "Failed to open file: " + filePath + " (fd: " + fd + ", errno: " + errno + ")";
            
            if (errno == ENOENT) {
                errorMsg += " - File not found";
            } else if (errno == EACCES) {
                errorMsg += " - Permission denied";
            } else if (errno == EISDIR) {
                errorMsg += " - Is a directory";
            }
            
            throw new RuntimeException(errorMsg);
        }
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Buffer readBuffer = new Buffer(READ_CHUNK_SIZE);
            int totalBytesRead = 0;
            
            while (true) {
                long bytesRead = Helper.syscall(Helper.SYS_READ, fd, readBuffer.address(), (long)READ_CHUNK_SIZE);
                
                if (bytesRead < 0) {
                    int errno = api.errno();
                    throw new RuntimeException("Read error on file: " + filePath + " (errno: " + errno + ")");
                }
                
                if (bytesRead == 0) {
                    // EOF reached
                    break;
                }
                
                if (totalBytesRead + bytesRead > MAX_PAYLOAD_SIZE) {
                    throw new RuntimeException("Payload too large: " + (totalBytesRead + bytesRead) + " bytes (max: " + MAX_PAYLOAD_SIZE + ")");
                }
                
                byte[] chunk = new byte[(int)bytesRead];
                for (int i = 0; i < bytesRead; i++) {
                    chunk[i] = readBuffer.getByte(i);
                }
                
                outputStream.write(chunk);
                totalBytesRead += bytesRead;
                
                if (bytesRead < READ_CHUNK_SIZE) {
                    break;
                }
            }
            
            if (totalBytesRead == 0) {
                throw new RuntimeException("File is empty or could not be read: " + filePath);
            }
            
            Status.println("Successfully read " + totalBytesRead + " bytes from: " + filePath);
            return outputStream.toByteArray();
            
        } finally {
            long closeResult = Helper.syscall(Helper.SYS_CLOSE, fd);
            if (closeResult < 0) {
                int errno = api.errno();
                Status.println("Warning: Failed to close file descriptor " + fd + " (error: " + closeResult + ", errno: " + errno + ")");
            }
        }
    }
        

    public static boolean fileExists(String filePath) {
        try {
            Text pathText = new Text(filePath);
            
            // Try to open the file for reading
            long fd = Helper.syscall(Helper.SYS_OPEN, pathText.address(), (long)O_RDONLY, 0L);
            
            if (fd >= 0) {
                // File exists and was opened successfully, close it
                Helper.syscall(Helper.SYS_CLOSE, fd);
                return true;
            } else {
                int errno = api.errno();
                if (errno == ENOENT) {
                    // File doesn't exist
                    return false;
                } else if (errno == EACCES) {
                    // File exists but permission denied - still counts as existing
                    Status.println("Warning: File exists but access denied: " + filePath);
                    return true;
                } else if (errno == EISDIR) {
                    // Path is a directory, not a file
                    Status.println("Warning: Path is a directory: " + filePath);
                    return false;
                }
                return false;
            }
            
        } catch (Exception e) {
            Status.println("Error checking file existence: " + e.getMessage());
            return false;
        }
    }

    public static void executePayloadFromPath(String payloadDataPath) {
        try {
            if (fileExists(payloadDataPath)) {
                Status.println("Loading payload from: " + payloadDataPath);
                
                loadFromFile(payloadDataPath);
                run();
                waitForPayloadToExit();
                
                Status.println("Payload execution completed successfully");
            } else {
                Status.println("Payload file not found: " + payloadDataPath);
            }
        } catch (Exception e) {
            Status.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void listenForPayloadOnPort(int port) {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        
        try {
            serverSocket = new ServerSocket(port);
            Status.println("BinLoader listening on port " + port + "...");
            NativeInvoke.sendNotificationRequest("BinLoader listening on port " + port + "...");
            
            while (true) {
                try {
                    clientSocket = serverSocket.accept();
                    Status.println("Accepted new connection from: " + clientSocket.getInetAddress());
                    
                    // Read payload data from client
                    byte[] payloadData = readPayloadFromSocket(clientSocket);
                    
                    Status.println("Received payload with size " + payloadData.length + " bytes (0x" + 
                                 Integer.toHexString(payloadData.length) + ")");
                    
                    // Load and execute payload
                    loadFromData(payloadData);
                    run();
                    waitForPayloadToExit();
                    
                    Status.println("Payload execution completed successfully");
                    
                    // Close client socket
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                            clientSocket = null;
                        } catch (IOException e) {
                            Status.println("Error closing client socket: " + e.getMessage());
                        }
                    }
                    
                    Status.println("Ready for next payload...");
                    
                } catch (Exception e) {
                    Status.println("Error processing payload: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Close client socket on error
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                            clientSocket = null;
                        } catch (IOException e2) {
                            Status.println("Error closing client socket: " + e2.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Status.println("Network server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close client socket
            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    Status.println("Error closing client socket: " + e.getMessage());
                }
            }
            
            // Close server socket
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    Status.println("Error closing server socket: " + e.getMessage());
                }
            }
        }
    }
    
    private static byte[] readPayloadFromSocket(Socket socket) throws IOException {
        InputStream inputStream = socket.getInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_CHUNK_SIZE];
        int bytesRead;
        
        // Read data until connection closes or no more data
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            
            // Check if we've exceeded max payload size
            if (outputStream.size() > MAX_PAYLOAD_SIZE) {
                throw new IOException("Payload too large: " + outputStream.size() + " bytes");
            }
        }
        
        byte[] payloadData = outputStream.toByteArray();
        outputStream.close();
        
        if (payloadData.length == 0) {
            throw new IOException("No payload data received");
        }
        
        return payloadData;
    }
    
    public static boolean isRunning() {
        return isRunning;
    }
    
    public static void stop() {
        if (binLoaderThread != null && binLoaderThread.isAlive()) {
            Status.println("Stopping BinLoader thread...");
            binLoaderThread.interrupt();
            isRunning = false;
        }
    }
}