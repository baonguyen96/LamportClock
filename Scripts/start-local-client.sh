#!/bin/sh

for i in 1 2 3 4 5
do
  echo "Starting client $i..."
  command="java -jar ../Client.jar ../Client/src/main/resources/Configurations/Client$i""Configuration.txt"
  (/bin/sh -c "$command" &)
done
