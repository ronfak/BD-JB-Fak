# BD-JB-1250
BD-JB for up to PS4 12.50  
~~This might be the exploit that was reported by TheFlow and patched at 12.52~~  
Nope TheFlow just confirmed this is not his exploit.  

Just take my early Christmas gift :)  

No this won't work at PS5.  

# Notes
RemoteLogger server is listening on port 18194.  
Use log_client.py to get the log.  
I recommend first running the log_client.py then starting the BD-J app.  

RemoteJarLoader server is listening on port 9025.  
Use payload_sender.py to send the jar file.  
You can use any other TCP payload sender too.  
Don't forget to set Main-Class in manifest.txt.  

Lapse is only for 9.00 to 12.02  
Lapse jar payload will automatically load the binary payload from /mnt/usb0/payload.bin and copy it to /data/payload.bin  
If binary payload is not present at either of these paths, it will start a binLoader server listening on port 9020.  
Use payload_sender.py to send the binary file.  
You can use any other TCP payload sender too.  
Always restart PS4 when exploit failed.  
Reopening BD-J app without rebooting will make exploit worse.  

For compiling I recommend using john-tornblom's bdj-sdk  
https://github.com/john-tornblom/bdj-sdk/  
Don't forget to replace bdjo file in BDMV  

# Credits
[TheFlow](https://github.com/theofficialflow) - For his BD-JB documentation and sources for native code execution  
[hammer-83](https://github.com/hammer-83) - For his PS5 Remote JAR Loader, it helped me a lot to learn how BD-J works  
[john-tornblom](https://github.com/john-tornblom) - For his BDJ-SDK, I couldn't have compiled PS4 BD-J without his BDJ-SDK  
[shahrilnet, null_ptr](https://github.com/shahrilnet/remote_lua_loader) - For lua lapse implementation, without it BD-J implementation was impossible  

