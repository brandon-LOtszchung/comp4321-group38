#!/bin/bash
# Run this script to start the search engine before the demo
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
export JAVA_HOME="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home"

echo "Starting search engine..."
/opt/homebrew/opt/tomcat/libexec/bin/catalina.sh start

echo "Waiting for server to start..."
sleep 8

echo "Opening browser..."
open http://localhost:8080/Search/

echo ""
echo "Done! The search engine should now be open in your browser."
echo "URL: http://localhost:8080/Search/"
