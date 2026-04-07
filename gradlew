#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
if [ ! -x "$JAVA_CMD" ]; then
  JAVA_CMD=java
fi
exec "$JAVA_CMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
