# Lamport's Clock

## Introduction

Implementation of Lamport's clock. Assume no transport failure.

## Requirements

1. Java 10 or higher
2. JDK 10 or higher
3. JRE 1.8 or higher
4. Maven
5. Windows 10 and/or Ubuntu LTS 18.4 (tested)

## How to run

### Build

1. Run the following command from the [root](.) directory:
```
mvn clean install -U
mvn package
```

2. A jar file should be generated under `Server/target/` and `Client/target` directories

### Run

#### Interactively

1. Start multiple instances of Server either from IDE or by running command `java -jar Name.jar` where `Name` is the rest of the jar file name in the `Server/target/` directory, then populate all necessary fields on them as prompted, but DO NOT choose to start any instance until all server instances are ready to be activated
2. Activate (choose start) all Server instances at the same time
3. Start multiple instances of Client either from IDE or by running command `java -jar Name.jar` where `Name` is the rest of the jar file name in the `Client/target/` directory, populate all necessary fields on them as prompted, but DO NOT choose to start any instance until all server instances are ready to be activated
4. Activate (choose start) all Client instances at the same time

#### Statically

1. Create configuration file for `Server` following [this format](./Server/src/main/resources/Configurations/ServerConfiguration.txt) with line 1 as the file directory, line 2 as server's IP address and port number, line 3 as list of other servers' IP addresses and ports separated by space
2. Create configuration file for `Client` following [this format](./Client/src/main/resources/Configurations/ClientConfiguration.txt) with line 1 as the client name, line 2 as list of other servers' IP addresses and ports separated by space
3. Run `java -jar Name.jar Path` where `Name` is the rest of the jar file name in the `Server/target/` directory and `Path` is the full path to the server's configuration file created above at the same time
4. Run `java -jar Name.jar Path` where `Name` is the rest of the jar file name in the `Client/target/` directory and `Path` is the full path to the client's configuration file created above at the same time
 