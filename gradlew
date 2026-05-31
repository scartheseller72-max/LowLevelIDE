#!/bin/sh
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then PRG="$link"
    else PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
[ -z "$JAVACMD" ] && JAVACMD=java
if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: Missing $CLASSPATH"
    echo "Run \`gradle wrapper\` once or copy gradle-wrapper.jar from another project."
    exit 1
fi
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
