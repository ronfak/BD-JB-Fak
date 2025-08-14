package org.bdj;

import java.io.*;
import java.net.*;
import java.lang.reflect.Method;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

public class InternalJarLoader implements Runnable {
    
    private static final String JAR_FILE_PATH = "/disc/payload.jar";
    
    public void run() {
        try {
            File jarFile = new File(JAR_FILE_PATH);
            
            if (!jarFile.exists()) {
                throw new FileNotFoundException("JAR file not found at: " + JAR_FILE_PATH);
            }
            
            if (!jarFile.canRead()) {
                throw new IOException("Cannot read JAR file at: " + JAR_FILE_PATH);
            }
            
            runJar(jarFile);
        } catch (IOException e) {
            Status.printStackTrace("JarLoader error", e);
        } catch (Exception e) {
            Status.printStackTrace("JarLoader error", e);
        }
    }
    
    private static void runJar(File jarFile) throws Exception {
        // Read the manifest to find the main class
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
        
        ClassLoader parentLoader = InternalJarLoader.class.getClassLoader();
        ClassLoader bypassRestrictionsLoader = new URLClassLoader(new URL[0], parentLoader) {
            protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("java.nio") || name.startsWith("javax.security.auth") || name.startsWith("javax.net.ssl")) {
                    return findSystemClass(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        
        URL jarUrl = jarFile.toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, bypassRestrictionsLoader);
        
        Class mainClass = classLoader.loadClass(mainClassName);
        
        Method mainMethod = mainClass.getMethod("main", new Class[]{String[].class});
        
        Status.println("Running " + mainClassName + " from " + jarFile.getAbsolutePath() + "...");
        mainMethod.invoke(null, new Object[]{new String[0]});
        
        Status.println(mainClassName + " execution completed");
    }
}