#!/bin/bash

# Build script for PDF Analyzer MCP Server

echo "Building PDF Analyzer..."

# Clean and package
./mvnw clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Maven build failed"
    exit 1
fi

echo "✅ Build completed successfully"

# Build Docker image
echo "Building Docker image..."
docker build -t pdf-analyzer:latest .

if [ $? -ne 0 ]; then
    echo "❌ Docker build failed"
    exit 1
fi

echo "✅ Docker image built successfully"
echo "🚀 Ready to run: docker-compose up"