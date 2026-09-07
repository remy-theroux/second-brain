#!/bin/sh
set -e

# Hot reload in a container, in two processes:
#
#  1. A continuous Gradle compiler (`-t classes`) recompiles the modified
#     sources into build/classes/java/main. It uses a separate
#     --project-cache-dir so as NOT to conflict on the lock with the bootRun below.
#
#  2. bootRun starts the application. Spring Boot DevTools watches build/classes
#     and restarts the context as soon as a .class changes.
#
# Result: a .java is edited on the host -> recompilation -> automatic restart.

./gradlew --no-daemon -t classes processResources &

exec ./gradlew --no-daemon bootRun
