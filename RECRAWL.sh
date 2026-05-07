#!/bin/bash
# Run this during Station 1 when the TA gives you a seed URL
# Usage: ./RECRAWL.sh <URL_FROM_TA>
#
# Example: ./RECRAWL.sh https://example.com/page.html

export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
export JAVA_HOME="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home"

SEED_URL="$1"
if [ -z "$SEED_URL" ]; then
  echo "Usage: ./RECRAWL.sh <SEED_URL>"
  exit 1
fi

echo "Stopping Tomcat..."
/opt/homebrew/opt/tomcat/libexec/bin/catalina.sh stop 2>/dev/null
sleep 3

echo "Deleting old database..."
rm -f phase2/webapp/spider_db.db phase2/webapp/spider_db.lg

echo "Crawling 30 pages from: $SEED_URL"
cd phase2
/opt/homebrew/opt/openjdk/bin/java \
  -cp "target/classes:webapp/WEB-INF/lib/htmlparser.jar:webapp/WEB-INF/lib/jdbm-1.0.jar" \
  comp4321.Spider "$SEED_URL" 30

echo ""
echo "Crawl done. Rebuilding and redeploying..."
/opt/homebrew/bin/mvn package -DskipTests -q
cp target/Search.war /opt/homebrew/opt/tomcat/libexec/webapps/Search.war
cd ..

echo "Starting Tomcat..."
export JAVA_HOME="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home"
/opt/homebrew/opt/tomcat/libexec/bin/catalina.sh start

echo ""
echo "Waiting for server..."
sleep 8

echo "Opening browser..."
open http://localhost:8080/Search/

echo ""
echo "Done! New crawl is live at http://localhost:8080/Search/"
