#!/bin/bash

export JAVA_HOME="$HOME/launcher/jdk/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
export LDPATH="$JAVA_HOME/lib/:$JAVA_HOME/lib/server/:$LDPATH"
authbind --deep $JAVA_HOME/bin/java -cp "$HOME/launcher/lib/*" codes.writeonce.launcher.DryRunMain "$@"
