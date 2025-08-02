package org.bdj;

import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;

import org.bdj.Status;
import org.bdj.api.NativeInvoke;

import org.bdj.sandbox.XletManagerExploit;

public class InitXlet implements Xlet {
	
    public void initXlet(XletContext context) {
		Status.println("BD-J init");
		try {
            Status.println("Triggering sandbox escape exploit...");
            
            if (XletManagerExploit.trigger()) {
                Status.println("Exploit success - sandbox escape achieved");
				NativeInvoke.sendNotificationRequest("Hello World");
            } else {
                Status.println("Exploit failed - sandbox still active");
            }
            
        } catch (Exception e) {
            Status.printStackTrace("Error in initXlet: ", e);
        }
		
    }
    
	public void startXlet() {

	}

	public void pauseXlet() {

	}

	public void destroyXlet(boolean unconditional) {

	}
}



