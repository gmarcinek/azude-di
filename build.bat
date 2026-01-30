@echo off
REM Build script for PDF Analyzer MCP Server

echo Building PDF Analyzer...

REM Clean and package
call mvnw.cmd clean package -DskipTests

if %errorlevel% neq 0 (
    echo ❌ Maven build failed
    exit /b %errorlevel%
)

echo ✅ Build completed successfully

REM Build Docker image
echo Building Docker image...
docker build -t pdf-analyzer:latest .

if %errorlevel% neq 0 (
    echo ❌ Docker build failed
    exit /b %errorlevel%
)

echo ✅ Docker image built successfully
echo 🚀 Ready to run: docker-compose up