# Spring Boot Deployment Guide: Brand New Server

This guide outlines the steps to deploy a Spring Boot application to a **brand new** Linux server (e.g., a fresh Linode Ubuntu instance) that has nothing installed yet.

## 1. Initial Server Setup (VPS)

SSH into your new server as `root` and run these commands to install the essential software stack:

```bash
# 1. Update package lists
apt update && apt upgrade -y

# 2. Install Java (JRE 17 for Spring Boot 3)
apt install openjdk-17-jre-headless -y

# 3. Install Nginx (Web Server)
apt install nginx -y

# 4. Install Certbot (For Free SSL Certificates)
apt install certbot python3-certbot-nginx -y

# 5. Install MySQL Server (If your database is hosted on this same machine)
apt install mysql-server -y
```

## 2. Database Setup (If using MySQL locally)

1. Open the MySQL prompt:
   ```bash
   mysql
   ```
2. Create your database and user:
   ```sql
   CREATE DATABASE my_database;
   CREATE USER 'my_user'@'localhost' IDENTIFIED BY 'my_password';
   GRANT ALL PRIVILEGES ON my_database.* TO 'my_user'@'localhost';
   FLUSH PRIVILEGES;
   EXIT;
   ```
3. Update your Spring Boot `application.properties` locally to use these credentials and the `jdbc:mysql://localhost:3306/...` URL.

## 3. Project & CI/CD Setup (Local)

1. **Port:** Since the server is empty, you can leave `server.port=8080` in your `application.properties`.
2. **GitHub Secrets:** Add your new server's IP address, username (`root`), and SSH Private Key to your GitHub repository secrets so GitHub Actions can connect.
3. **Deploy Workflow:** Set up the standard `.github/workflows/deploy.yml` file to copy the JAR and restart the systemd service.

## 4. App Service Preparation (VPS)

1. **Create the Folder:**
   ```bash
   mkdir -p /opt/myapp
   ```
2. **Create the Systemd Service:**
   ```bash
   nano /etc/systemd/system/myapp.service
   ```
   *Paste the following:*
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
3. **Enable it:**
   ```bash
   systemctl daemon-reload
   systemctl enable myapp.service
   ```

## 5. First Deploy

Run `git push` on your local machine to trigger the GitHub action. It will build your app, copy it to the VPS, and start the background service.

## 6. Nginx & SSL Setup (VPS)

1. **Create the Nginx File:**
   ```bash
   nano /etc/nginx/sites-available/myapp
   ```
   *Paste this block. Since this is the only app on the server, you don't even need a secret URL if you don't want one, but here is the setup for a standard domain:*
   ```nginx
   server {
       listen 80;
       server_name mydomain.com;

       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```
2. **Enable Nginx Config:**
   ```bash
   ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/
   nginx -t
   systemctl restart nginx
   ```
3. **Install SSL:**
   ```bash
   certbot --nginx -d mydomain.com
   ```

Your app is now live, secured, and automatically deploying!
