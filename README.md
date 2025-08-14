# BD-JB-1250
BD-JB for up to PS4 12.50  
~~This might be the exploit that was reported by TheFlow and patched at 12.52~~  
Nope TheFlow just confirmed this is not his exploit.  

Just take my early Christmas gift :)  

No this won't work at PS5.  

Here’s your updated guide with **shahrilnet** and **null\_ptr** added to the end of the credits:
Here’s your **updated guide** with the extra note about sending `Lapse.jar` when using `RemoteJarLoader-1.1.iso` added in the relevant spots.

---

## Lapse Exploit (Firmware 9.00 – 12.02 Only)

### Preparation

* **Release Contents**:

  * **`Lapse.iso`** — Contains the Lapse JAR payload built in.

    * This JAR will automatically load the binary payload from `/mnt/usb0/payload.bin` and copy it to `/data/payload.bin`.
  * **`RemoteJarLoader-1.1.iso`** — Allows you to send your **own** JAR payload using RemoteJarLoader (see section below).

    * If you want to send the **Lapse JAR payload** while using `RemoteJarLoader-1.1.iso`, you can send `Lapse.jar` instead of building your own.
* **USB Requirements**:

  * Format your USB drive as **exFAT** or **FAT32**.
  * Place the GoldHEN binary at the **root** of the USB drive:

    * Rename `goldhen.bin` → `payload.bin`.
  * **First** insert the USB drive **then** insert the Blu-ray disc.
* **PS4 Settings**:

  * Ensure **HDCP** is **enabled** (necessary for Blu-ray playback).
  * **Blu-ray playback activation**:

    * If Blu-ray disc playback has never been used before on your PS4, the console will require a one-time internet connection to activate the feature.
    * Allow this connection, but **disable automatic system updates** in Settings to prevent unintended firmware upgrades.

### Payload Workflow

* When using **`Lapse.iso`**:

  * Automatically loads `/mnt/usb0/payload.bin` → copies to `/data/payload.bin`.
  * If `payload.bin` is missing, launches a **binLoader server** on **port 9020**.

    * Use `payload_sender.py` (or any TCP payload sender — see **RemoteJarLoader** section) to send the binary payload.
* When using **`RemoteJarLoader-1.1.iso`**:

  * Allows sending your own JAR payload to port **9025** (see section below).
  * If you want the same behavior as `Lapse.iso`, simply send `Lapse.jar` as the payload.

### Logging

* Logs can be captured using **RemoteLogger** (see instructions below).

### Important Care Notes

* If the exploit fails, **always restart the PS4**.
* **Do not** reopen the BD-J app without rebooting — it **worsens exploit stability**.

---

## RemoteLogger

* Server listens on **port 18194**.
* Use `log_client.py` to retrieve logs.
* **Usage Sequence**:

  1. Run `log_client.py` **first**.
  2. Then start the **BD-J app**.

---

## RemoteJarLoader

* Server listens on **port 9025**.
* Use `payload_sender.py` to send the JAR file.
* Alternatively, any TCP payload sender may be used.
* Ensure the **`Main-Class`** is correctly set in `manifest.txt`.
* To send the Lapse JAR payload with `RemoteJarLoader-1.1.iso`, use `Lapse.jar`.

---

## Burning the Blu-ray ISO to Disc

This exploit requires burning a Blu-ray ISO file onto a disc. Recommended tools:

* **Windows**: Use **ImgBurn** — a lightweight and reliable disc-burning application.

  * Official site: [ImgBurn Download](https://www.imgburn.com/)

* **Linux**: Use **K3b** — KDE’s disc-burning application with full Blu-ray support.

  * Official site: [K3b on KDE Apps](https://apps.kde.org/k3b/)

---

## Summary: Execution Checklist

1. Choose ISO:

   * `Lapse.iso` → Uses built-in Lapse JAR payload.
   * `RemoteJarLoader-1.1.iso` → Send your own JAR payload (or `Lapse.jar` for default behavior).
2. Format USB as **exFAT/FAT32** → Rename and place `payload.bin` (renamed from `goldhen.bin`) in root.
3. Insert USB **first**, then Blu-ray disc.
4. Ensure **HDCP enabled** in PS4 settings.
5. If Blu-ray playback is not yet activated, allow one-time internet connection — **then disable auto-updates**.
6. Burn the chosen ISO using ImgBurn (Windows) or K3b (Linux).
7. Launch payload:

   * From USB (Lapse.iso) or binLoader server (port 9020).
   * Send JAR via RemoteJarLoader (port 9025) if using RemoteJarLoader ISO.
8. Capture logs via RemoteLogger (port 18194).
9. If failure occurs, **reboot PS4 — do not simply reopen BD-J app**.

---

## Compilation Recommendation

For compiling, I recommend using **[john-tornblom's bdj-sdk](https://github.com/john-tornblom/bdj-sdk/)**.
Don’t forget to replace the **BDJO** file in `BDMV`.

---

## Credits

* **[TheFlow](https://github.com/theofficialflow)** — For BD-JB documentation and native code execution sources.
* **[hammer-83](https://github.com/hammer-83)** — For the PS5 Remote JAR Loader, which helped me understand BD-J internals.
* **[john-tornblom](https://github.com/john-tornblom)** — For BDJ-SDK, without which I couldn’t have compiled PS4 BD-J.
* **[shahrilnet, null\_ptr](https://github.com/shahrilnet/remote_lua_loader)** — For Lua Lapse implementation, without which BD-J implementation was impossible.

---






