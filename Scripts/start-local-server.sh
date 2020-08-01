#!/bin/sh

thisDirectory=$(pwd)
cd "$(dirname "$thisDirectory")" || exit

for i in 1 2 3
do
  echo "Starting server $i..."
  command="java -jar ./Server.jar ./Server/src/main/resources/Configurations/Server$i""Configuration.txt"
  (/bin/sh -c "$command" &)
done

cd "$thisDirectory" || exit
