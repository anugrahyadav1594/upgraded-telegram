@echo off
title REALCHAT - Startup
echo ========================================================
echo   Starting REALCHAT Demo
echo ========================================================
echo.

cd /d "%~dp0"

echo 1. Starting Backend Mock Server (Port 3080)...
set PREVIEW_PORT=3080
start "REALCHAT Backend" /min node tools\preview\multi-server.js

timeout /t 2 /nobreak >nul

echo 2. Starting Frontend Dev Server (Port 5173)...
set VITE_BACKEND_URL=http://localhost:3080
cd frontend
start "REALCHAT Frontend" /min npm run dev
cd ..

timeout /t 3 /nobreak >nul

echo 3. Creating Public Internet Tunnel (Serveo)...
echo.
echo --------------------------------------------------------
echo Look for the URL below (https://...serveousercontent.com)
echo Share that link with anyone on their phone or laptop!
echo --------------------------------------------------------
echo.
ssh -o StrictHostKeyChecking=no -R 80:localhost:5173 serveo.net
