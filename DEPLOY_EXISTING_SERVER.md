# Spring Boot Deployment Guide: Existing Server

This guide outlines the steps to deploy a new Spring Boot application to a Linux server that is **already hosting other applications** (meaning Nginx, Java, and MySQL are already installed and running).

## 1. Project Preparation (Local)

1. **Choose a Unique Port:** 
   Since the server already hosts other apps, port 8080 is likely in use.
   In `src/main/resources/application.properties`:
   ```properties
   server.port=8085
   ```

2. **Frontend Paths:**
   If you plan to host the app on a secret URL path using Nginx, ensure all static assets (like images) and API calls in your HTML/JS use **relative paths** (e.g., `src="logo.jpg"` instead of `src="/logo.jpg"`). Do not use `server.servlet.context-path` in Spring Boot, as Nginx will handle the path stripping.

3. **Configure CI/CD:**
   Create `.github/workflows/deploy.yml`. Ensure the `mv` command explicitly targets your jar file to prevent wildcard conflicts if multiple jars accumulate on the server:
   ```bash
   mv -f /opt/myapp/myapp-0.0.1-SNAPSHOT.jar /opt/myapp/myapp.jar
   ```

## 2. Server Preparation (VPS)

1. **Create the Application Directory:**
   ```bash
   mkdir -p /opt/myapp
   ```

2. **Create the Systemd Service File:**
   ```bash
   nano /etc/systemd/system/myapp.service
   ```
   *Paste the following config (update paths and descriptions):*
   ```ini
   [Unit]
   Description=My Spring Boot App
   After=syslog.target network.target

   [Service]
   User=root
   ExecStart=/usr/bin/java -jar /opt/myapp/myapp.jar
   SuccessExitStatus=143
   Restart=always
   RestartSec=10

   [Install]
   WantedBy=multi-user.target
   ```

3. **Enable the Service:**
   ```bash
   systemctl daemon-reload
   systemctl enable myapp.service
   ```

## 3. Deploy

1. Commit and push your code to GitHub.
2. Wait for the GitHub Action to finish copying the JAR to the VPS and restarting the service.
3. Check the status to ensure it didn't crash:
   ```bash
   systemctl status myapp.service
   journalctl -u myapp.service -n 50 --no-pager
   ```

## 4. Nginx Configuration (VPS)

1. **Create the Nginx File:**
   ```bash
   nano /etc/nginx/sites-available/myapp
   ```
   *Paste the following block (notice the trailing slashes on the location and proxy_pass!)*
   ```nginx
   server {
       listen 80;
       server_name mydomain.com;

       # The trailing slashes here are critical for stripping the path!
       location /my-secret-url/ {
           proxy_pass http://localhost:8085/;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```

2. **Enable the Nginx File:**
   ```bash
   # IMPORTANT: Do not skip this step!
   ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/
   ```

3. **Test and Restart:**
   ```bash
   nginx -t
   systemctl restart nginx
   ```

4. **Install SSL:**
   If you try to visit `https://` before this step, you will be routed to the wrong app!
   ```bash
   certbot --nginx -d mydomain.com
   ```

Your app is now live!
