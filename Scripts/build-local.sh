#!/bin/sh
echo ""
echo "Building..."
echo ""

start=$(date +%s)
thisDirectory=$(pwd)

cd "$(dirname "$thisDirectory")" || exit

mvn clean install -U
mvn package

echo "Moving jar file..."

for module in Server Client
do
  mv "./${module}/target/${module}-1.0-SNAPSHOT-jar-with-dependencies.jar" "./${module}.jar" --force
done

cd "${thisDirectory}" || exit

end=$(date +%s)
duration=$((end - start))

echo "Finished after ${duration} seconds"
