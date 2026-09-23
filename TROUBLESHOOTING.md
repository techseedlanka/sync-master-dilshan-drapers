# Spring Boot & Nginx Troubleshooting Guide

When your web application fails to load or crashes, it can be frustrating to figure out whether the problem is Nginx, Java, your Database, or your Network. 

This guide provides a step-by-step framework to isolate the issue by running specific diagnostic commands.

---

## 1. Is the Java Application Running?

**Command:**
```bash
systemctl status myapp.service
```

**What you gain:**
- `Active: active (running)`: The app is alive. Proceed to step 2.
- `Active: activating (auto-restart) (Result: exit-code)`: The app crashed and is trying to restart.
- `status=1/FAILURE` or `code=exited, status=143`: The JVM died. 

**Diagnosis:**
If it crashed, you need to know *why* the Java code failed. Proceed immediately to Step 1A.

---

### 1A. Why did the Java App Crash?

**Command:**
```bash
journalctl -u myapp.service -n 50 --no-pager
```
*(This fetches the last 50 lines of the Java console logs)*

**What to look for in the output:**
1. `Web server failed to start. Port 8080 was already in use.`
   * **The Issue:** Another application is already using this port.
   * **The Fix:** Change `server.port=8085` in `application.properties`.
2. `Communications link failure` or `Connection refused` (HikariPool)
   * **The Issue:** The app cannot talk to MySQL.
   * **The Fix:** Ensure MySQL is running (`systemctl status mysql`), the IP/Port is correct, and the user credentials in `application.properties` are valid.
3. `OutOfMemoryError` or `status=137`
   * **The Issue:** Your VPS ran out of RAM, and the Linux kernel killed Java.
   * **The Fix:** Upgrade your VPS RAM, or add swap space.

---

## 2. Is Nginx configured correctly?

If Java is running perfectly, but you still see a **404 Not Found**, **502 Bad Gateway**, or get routed to the wrong app, Nginx is likely the culprit.

### 2A. Syntax Check

**Command:**
```bash
nginx -t
```
**What you gain:**
If it says `test failed`, there is a typo in your config (e.g., missing a `;` or a `server { }` block). Nginx will **not** apply your new configuration until this says `syntax is ok`.

### 2B. Check Enabled Sites

**Command:**
```bash
ls -l /etc/nginx/sites-enabled/
```
**What you gain:**
If your configuration file is in `/etc/nginx/sites-available/` but does **not** show up in this list, Nginx is completely ignoring it. 
* **The Fix:** You must link it using `ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/`.

---

## 3. Isolating the Connection (The "curl" Test)

If everything seems correct but the browser still shows an error, we need to find out exactly where the chain is breaking.

### Test A: Bypass Nginx (Talk directly to Java)

Run this on your VPS terminal:
```bash
curl -v http://localhost:8085/
```
*(Replace 8085 with your actual Java port)*

**What you gain:**
- If it prints out a bunch of HTML code: **Java is working perfectly.** The problem is 100% inside Nginx or your domain setup.
- If it prints a 404 error: **Java is the problem.** Spring Boot is running, but it cannot find your `index.html` file, or your controllers are misconfigured.

### Test B: Test Nginx Locally

Run this on your VPS terminal:
```bash
curl -v http://localhost/my-secret-url/ -H "Host: mydomain.com"
```

**What you gain:**
- This simulates exactly what Nginx does when a web request comes in. If this returns HTML, Nginx is configured perfectly. If it returns a 404 or routes to the wrong app, your Nginx `server_name` or `location` block is wrong.

---

## 4. HTTPS / SSL Issues

If your site works on `http://` but shows a completely different app or a 404 on `https://`, it is because of SSL misconfiguration.

**The Issue:** 
When you visit `https://`, Nginx looks for a server block with `listen 443 ssl;`. If your app only has `listen 80;`, Nginx falls back to the default SSL app on your server (which is why you see the wrong app!).

**The Fix:**
Run Certbot to automatically inject the SSL configuration into your file:
```bash
certbot --nginx -d mydomain.com
```

---

## Quick Summary Cheat Sheet

| Symptom | Probable Cause | Command to Run |
| :--- | :--- | :--- |
| Loading spinner forever | Java app is frozen or DB timeout | `journalctl -u app.service -e` |
| 502 Bad Gateway | Java app crashed or wrong port in Nginx | `systemctl status app.service` |
| Nginx 404 Error | Wrong URL path or file not linked | `ls -l /etc/nginx/sites-enabled/` |
| Tomcat 404 Error | Nginx trailing slash is missing | `nano /etc/nginx/sites-available/app` |
| Works on HTTP, not HTTPS | Certbot SSL was never installed | `certbot --nginx -d domain.com` |
