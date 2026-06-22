#!/usr/bin/env bash

##############################################################################
#
#   Gradle start up script for POSIX
#
##############################################################################

# Resolve the location of the script
APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Build the classpath
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Execute Gradle
exec "$JAVACMD" \
    -Xmx64m \
    -Xms64m \
    -Dorg.gradle.appname="${0##*/}" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
