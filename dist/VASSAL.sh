#!/bin/sh

#
# Execute this file to launch VASSAL on Unix
#

# Find absolute path where VASSAL is installed
INSTALL_DIR=$(cd "$(dirname "$0")"; pwd)

# Launch VASSSAL
java -Duser.dir="$INSTALL_DIR" -classpath "$INSTALL_DIR"/lib/Vengine.jar --add-exports java.desktop/sun.java2d.cmm=ALL-UNNAMED VASSAL.launch.ModuleManager "$@"
