package org.bdj;

import java.io.*;
import java.net.*;
import java.lang.reflect.Method;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

import org.bdj.api.API;
import org.bdj.api.Buffer;

public class RemoteJarLoader implements Runnable {
    
    public RemoteJarLoader() {
		cleanupOldTempFiles();
	}
    
    public void run() {
        try {
            ServerSocket server = new ServerSocket(9025);
            Status.println("JAR Loader listening on port 9025...");
            
            while (true) {
                Socket client = server.accept();
                Status.println("Client connected");
                
                try {
                    processReceivedData(client);
                } catch (Exception e) {
                    Status.printStackTrace("Error processing Data", e);
                }
                
                client.close();
				Status.println("Waiting for next file on port 9025...");
            }
        } catch (IOException e) {
            Status.printStackTrace("Server error", e);
        }
    }
    private static void processReceivedData(Socket client) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream input = client.getInputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;

        Status.println("Receiving data...");
        while ((bytesRead = input.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }

        byte[] data = baos.toByteArray();
        Status.println("Received " + data.length + " bytes total");

	// Check if it's a JAR file (starts with ZIP local file header 0x50 0x4B 0x03 0x04)
	if (data.length >= 4 &&
	    (data[0] & 0xFF) == 0x50 &&
	    (data[1] & 0xFF) == 0x4B &&
	    (data[2] & 0xFF) == 0x03 &&
	    (data[3] & 0xFF) == 0x04) {
	    runJar(data);
	} else {
	    runNativeBin(data);
	}
    }
/*
    private static void loadAndRunJar(Socket client) throws Exception {
        File tempJar = File.createTempFile("received", ".jar");
        tempJar.deleteOnExit();
        
        InputStream input = client.getInputStream();
        FileOutputStream output = new FileOutputStream(tempJar);
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        int totalBytes = 0;
        
        Status.println("Receiving JAR...");
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
            Status.println("Received " + totalBytes + " bytes");
        }
        
        output.close();
        Status.println("JAR received: " + totalBytes + " bytes total");
        
        runJar(tempJar);
        
        tempJar.delete();
    }
 */   
private static void runJar(byte[] jarData) throws Exception {
    File tempJar = File.createTempFile("received", ".jar");
    tempJar.deleteOnExit();

    try (FileOutputStream output = new FileOutputStream(tempJar)) {
        output.write(jarData);
    }

    runJarFromFile(tempJar);
    tempJar.delete();
}
	
private static void runJarFromFile(File jarFile) throws Exception {
    JarFile jar = new JarFile(jarFile);
    Manifest manifest = jar.getManifest();
    jar.close();

    if (manifest == null) {
        throw new Exception("No manifest found in JAR");
    }

    String mainClassName = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
    if (mainClassName == null) {
        throw new Exception("No Main-Class specified in manifest");
    }

    Status.println("Main class: " + mainClassName);

    ClassLoader parentLoader = RemoteJarLoader.class.getClassLoader();
    ClassLoader bypassRestrictionsLoader = new URLClassLoader(new URL[0], parentLoader) {
        protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("java.nio") || name.startsWith("javax.security.auth") || name.startsWith("javax.net.ssl")) {
                return findSystemClass(name);
            }
            return super.loadClass(name, resolve);
        }
    };

    URL jarUrl = jarFile.toURL();
    URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, bypassRestrictionsLoader);

    Class<?> mainClass = classLoader.loadClass(mainClassName);
    Method mainMethod = mainClass.getMethod("main", String[].class);

    Status.println("Running " + mainClassName + "...");
    mainMethod.invoke(null, (Object) new String[0]);
    Status.println("Execution completed");
}	
private static void runNativeBin(byte[] binData) throws Exception {
    Status.println("Running native payload...");

    API api = API.getInstance();
    long size = binData.length;

    // Allocate RWX memory using syscall 609 (sceKernelJitCreateSharedMemory)
    int prot = 0x1 | 0x2 | 0x4; // PROT_READ | PROT_WRITE | PROT_EXEC
    long sharedMemFd = api.syscall(609, size, prot);
    if (sharedMemFd < 0) {
        throw new RuntimeException("Failed to allocate shared memory: syscall 609 returned " + sharedMemFd);
    }

    // Map the shared memory into process space using syscall 615
    long mem = api.syscall(615, sharedMemFd, 0);
    if (mem < 0) {
        throw new RuntimeException("Failed to mmap shared memory: syscall 615 returned " + mem);
    }

    Status.println("Mapped memory at 0x" + Long.toHexString(mem));

    // Copy payload to allocated memory
    api.memcpy(mem, binData, size);

    // Call payload
    Status.println("Calling payload...");
    long result = api.call(mem);

    Status.println("Native payload returned: " + result);
}
    private void cleanupOldTempFiles() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            File tempFolder = new File(tempDir);
            File[] files = tempFolder.listFiles();
            
            if (files != null) {
                int cleanedCount = 0;
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    if (file.getName().startsWith("received") && file.getName().endsWith(".jar")) {
                        if (file.delete()) {
                            cleanedCount++;
                        }
                    }
                }
                if (cleanedCount > 0) {
                    Status.println("Cleaned up " + cleanedCount + " old temp JAR files");
                }
            }
        } catch (Exception e) {
            Status.println("Warning: Could not clean temp files: " + e.getMessage());
        }
    }
}
