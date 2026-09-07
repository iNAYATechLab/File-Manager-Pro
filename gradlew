#!/usr/bin/env sh

# Minimal Gradle wrapper launcher for File-Manager-Pro.
# Downloads the Gradle distribution specified in gradle/wrapper/gradle-wrapper.properties
# on first run (uses the same wrapper jar as a standard generated wrapper).

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

exec "$JAVA" -Xmx64m -Xms64m \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
