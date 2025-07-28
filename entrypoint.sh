#!/bin/sh

echo "The app is starting ..."
exec java -Dspring.profiles.active=${SPRING_ACTIVE_PROFILES} -jar tourguide-0.0.1-SNAPSHOT.jar
