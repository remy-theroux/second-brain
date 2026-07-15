#!/bin/sh
set -e

# Hot reload en conteneur, en deux processus :
#
#  1. Un compilateur Gradle en continu (`-t classes`) recompile les sources
#     modifiées vers build/classes/java/main. Il utilise un --project-cache-dir
#     séparé pour ne PAS entrer en conflit de lock avec le bootRun ci-dessous.
#
#  2. bootRun lance l'application. Spring Boot DevTools surveille build/classes
#     et redémarre le contexte dès qu'un .class change.
#
# Résultat : on édite un .java sur l'hôte -> recompilation -> restart automatique.

./gradlew --no-daemon -t classes processResources &

exec ./gradlew --no-daemon bootRun
