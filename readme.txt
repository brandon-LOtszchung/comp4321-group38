COMP4321 Group 38 — Web Search Engine
======================================

Seed URL : https://hitcslj.github.io/TestPages/testpage.htm
Max pages: 300  (DB currently has 299 crawled pages)

Project layout
--------------
  phase1/                    Phase 1: BFS spider + JDBM inverted index
    src/main/java/           Java source files (no package)
    pom.xml                  Maven build
  phase2/                    Phase 2: TF-IDF search engine + Tomcat web app
    src/main/java/comp4321/ Java source files (package comp4321)
    webapp/                  Tomcat web application
      index.jsp              Search home page
      result.jsp             Search results page
      WEB-INF/web.xml        Servlet descriptor
      WEB-INF/lib/           Runtime JARs (htmlparser, jdbm)
    pom.xml                  Maven build → produces target/Search.war
  design.txt                 JDBM database schema
  spider_result.txt          Crawler output (299 pages, Phase 2 format)
  stopwords.txt              153 English stop words

Prerequisites
-------------
  Java 11+  (OpenJDK 25 installed via Homebrew)
  Maven 3.6+
  Tomcat 10.1+  (installed via Homebrew: brew install tomcat)

  export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
  export JAVA_HOME="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home"

Build Phase 2
-------------
  cd phase2
  mvn clean package -DskipTests
  # Output: phase2/target/Search.war

Deploy to Tomcat
----------------
  cp phase2/target/Search.war \
     /opt/homebrew/opt/tomcat/libexec/webapps/Search.war

  export JAVA_HOME="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home"
  /opt/homebrew/opt/tomcat/libexec/bin/catalina.sh start

  App URL: http://localhost:8080/Search/
  To stop: /opt/homebrew/opt/tomcat/libexec/bin/catalina.sh stop

Crawl 300 pages from scratch (run from phase2/)
------------------------------------------------
  # Delete old DB first for a fresh crawl:
  rm -f webapp/spider_db.db webapp/spider_db.lg

  mvn clean package -DskipTests -q
  java -cp "target/classes:webapp/WEB-INF/lib/htmlparser.jar:webapp/WEB-INF/lib/jdbm-1.0.jar" \
       comp4321.Spider \
       https://hitcslj.github.io/TestPages/testpage.htm 300
  # DB written to phase2/webapp/spider_db.*

  # Rebuild WAR with new DB and redeploy:
  mvn package -DskipTests -q
  cp target/Search.war /opt/homebrew/opt/tomcat/libexec/webapps/Search.war

Generate spider_result.txt (run from phase2/)
---------------------------------------------
  java -cp "target/classes:webapp/WEB-INF/lib/htmlparser.jar:webapp/WEB-INF/lib/jdbm-1.0.jar" \
       comp4321.TestProgram webapp/spider_db ../spider_result.txt

Re-crawl 30 pages with a different seed (Station 1 live test)
-------------------------------------------------------------
  rm -f webapp/spider_db.db webapp/spider_db.lg
  java -cp "target/classes:webapp/WEB-INF/lib/htmlparser.jar:webapp/WEB-INF/lib/jdbm-1.0.jar" \
       comp4321.Spider <TA_SEED_URL> 30

DB schema (design.txt)
-----------------------
  15 JDBM HTree tables. Two separate inverted indexes:
    bodyInverted  — wordId -> {pageId -> [positions]}
    titleInverted — wordId -> {pageId -> [positions]}
  Positional posting lists support phrase search ("hong kong").
  Title match gets 5x weight boost in ranking.

Demo query examples (prepare these on paper for TA3)
----------------------------------------------------
  Single keyword   : book              (returns ~26 results)
  Multi-keyword    : hong kong university
  Phrase search    : "hong kong"       (returns ~38 results)
  Zero results     : xyzzy quux        (returns 0 results)
  Title boost demo : news              (pages with "News" in title rank #1)
  Stop-word only   : the of a          (returns 0 results)
