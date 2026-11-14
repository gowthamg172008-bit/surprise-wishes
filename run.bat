@echo off
setlocal
cd /d "%~dp0"
if not defined PORT set "PORT=8080"
set "APP_URL=http://localhost:%PORT%"

curl.exe --silent --fail "%APP_URL%/api/health" 2>nul | findstr /x "little-moments-ok" >nul
if not errorlevel 1 (
    echo Little Moments is already running. Opening it in your browser...
    start "" "%APP_URL%"
    exit /b 0
)

if not exist build\classes mkdir build\classes
javac --add-modules jdk.httpserver -encoding UTF-8 -d build\classes src\main\java\app\Main.java
if errorlevel 1 (
    echo.
    echo Java compilation failed. Check the error above and make sure JDK 21 is installed.
    pause
    exit /b 1
)

echo Starting Little Moments on %APP_URL%...
start "Little Moments Server" /D "%CD%" java --add-modules jdk.httpserver -cp build\classes app.Main
for /l %%i in (1,1,30) do (
    curl.exe --silent --fail "%APP_URL%/api/health" 2>nul | findstr /x "little-moments-ok" >nul
    if not errorlevel 1 (
        echo Server is ready. Opening your browser...
        start "" "%APP_URL%"
        exit /b 0
    )
    timeout /t 1 /nobreak >nul
)

echo.
echo The server did not start on port %PORT%.
echo Check the Little Moments Server window for the Java error. Another app may be using this port.
pause
exit /b 1
