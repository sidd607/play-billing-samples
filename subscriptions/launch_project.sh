#!/bin/bash
#
# Launches publicly available Android Studio with this Gradle project in Piper.
#
# This script will call gmscore/sdk/tools/samples/launch_project.sh based on
# your current CitC client path as determined by `p4 g4d`.
#
# Usage:
# google3$ third_party/googlesamples/[your_project]/launch_project.sh
#
# See go/sdk-sample-launch-project for additional info.

LAUNCHED_PROJECT_DIR="$(dirname $0)"
CITC_DIRECTORY="$(pwd)"
"${CITC_DIRECTORY}/gmscore/sdk/tools/samples/launch_project.sh" \
  "${LAUNCHED_PROJECT_DIR}"
