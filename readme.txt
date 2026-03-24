COMP4321 Search Engine - Phase 1
=================================

Build (requires Java 11+ and Maven 3.6+):

  mvn clean package

  Produces: target/search-engine-1.0.jar

Run the spider (crawls 30 pages from testpage.htm):

  java -cp target/search-engine-1.0.jar Spider

  Optional: java -cp target/search-engine-1.0.jar Spider <startUrl> <maxPages>

Generate spider_result.txt:

  java -cp target/search-engine-1.0.jar TestProgram

Both commands should be run from the project root directory.
stopwords.txt must be in the current directory when running Spider.
DB files (spider_db.db, spider_db.lg) are created in the current directory.

To re-crawl from scratch, delete spider_db.db and spider_db.lg first.
