# BD-JB-1250
BD-JB for up to PS4 12.50  
~~This might be the exploit that was reported by TheFlow and patched at 12.52~~  
Nope TheFlow just confirmed this is not his exploit.  

Just take my early Christmas gift :)  

No this won't work at PS5.  


---

## Lapse Exploit (Firmware 9.00 – 12.02 Only)

---

### 1. Release Contents

You will find **two ISOs** in the release:

* **`Lapse.iso`** — Contains the Lapse JAR payload built in.

  * Automatically loads `/mnt/usb0/payload.bin` and copies it to `/data/payload.bin`.
  * If `payload.bin` is missing, launches a **binLoader server** on port **9020** to receive a binary payload.
* **`RemoteJarLoader-1.1.iso`** — Allows you to send your own JAR payload via port **9025**.

  * If you want the same behavior as `Lapse.iso` while using this, send **`Lapse.jar`** as the payload.

---

### 2. Preparing Your USB

* Format as **exFAT** or **FAT32**.
* Place your homebrew enabler payload (e.g. GoldHEN, ps4-hen) at the root and rename it to `payload.bin`
* For `Lapse.iso`: payload.bin will be loaded automatically.
* For `RemoteJarLoader-1.1.iso`:

  * Send a custom JAR payload, or send **Lapse.jar** to mimic `Lapse.iso`’s behavior.

---

### 3. PS4 Settings

* **Enable HDCP** in Settings (required for Blu-ray playback).
* If Blu-ray playback is **not yet activated** which can happen if you are using blue-ray disc for the first time:

  * **Disable Automatic Updates** first to avoid firmware upgrades.
  * Connect to the internet once to activate it.

---

### 4. Running the Exploit

**Step-by-step:**

1. Insert the **USB drive first**.
2. Insert the **Blu-ray disc** (burned with `Lapse.iso` or `RemoteJarLoader-1.1.iso`).
3. Wait for payload delivery:

   * With **Lapse.iso**: payload.bin loads from USB → /data/payload.bin
   * With **RemoteJarLoader**: send JAR payload to port **9025**.
4. If exploit fails → **Restart the PS4** before retrying.

   * Do **not** simply reopen the BD-J app — stability will drop.

---

### 5. Logging (Optional)

* BD-J app logs are sent over network.
* Use **RemoteLogger**:

  * Server listens on port **18194**.
  * Run `log_client.py` first, then launch the BD-J app.

---

### 6. Burning the Blu-ray ISO

* **Windows**: Use **[ImgBurn](https://www.imgburn.com/?utm_source=chatgpt.com)**.
* **Linux**: Use **[K3b](https://apps.kde.org/k3b/?utm_source=chatgpt.com)**.

---

### 7. Summary Table

| ISO Type                | What it Does               | Ports Used            | Payload Behavior                                            |
| ----------------------- | -------------------------- | --------------------- | ----------------------------------------------------------- |
| Lapse.iso               | Built-in Lapse JAR payload | 9020 (if bin missing) | Loads `/mnt/usb0/payload.bin` → `/data/payload.bin`         |
| RemoteJarLoader-1.1.iso | Custom JAR payload         | 9025                  | Send `Lapse.jar` for default Lapse behavior or your own JAR |

---

### 8. Compilation Recommendation

Use **[john-tornblom's bdj-sdk](https://github.com/john-tornblom/bdj-sdk/)** for compiling.
Replace the BDJO file in `BDMV` when building.

---

### 9. Credits

* **[TheFlow](https://github.com/theofficialflow)** — BD-JB documentation & native code execution sources.
* **[hammer-83](https://github.com/hammer-83)** — PS5 Remote JAR Loader reference.
* **[john-tornblom](https://github.com/john-tornblom)** — BDJ-SDK used for compilation.
* **[shahrilnet, null\_ptr](https://github.com/shahrilnet/remote_lua_loader)** — Lua Lapse implementation, without which BD-J was impossible.

---









