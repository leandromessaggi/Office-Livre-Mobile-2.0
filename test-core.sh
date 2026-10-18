#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/coretest
javac -encoding UTF-8 -d build/coretest app/src/main/java/com/officelivre/mobile/core/*.java tests/CoreTests.java tests/V2Tests.java tests/ParseAndroid.java
java -cp build/coretest CoreTests
java -cp build/coretest V2Tests samples
java -cp build/coretest ParseAndroid \
    app/src/main/java/com/officelivre/mobile/MainActivity.java \
    app/src/main/java/com/officelivre/mobile/PdfPageView.java
