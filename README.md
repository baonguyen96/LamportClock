# Lamport's Clock

## Introduction

Implementation of Lamport's clock. Assume no transport failure.

## Requirements

1. Java 13
2. JDK 13
3. JRE 1.8
4. Windows 10 and Ubuntu LTS 18.4 (tested)

## How to run

### Build

1. Go to [Utility](./Utility), [Server](./Server) and [Client](./Client) directory **in that order**

2. In each directory, run the following command:
```
mvn clean install -U
mvn package
```

### Run

Instructions here

## Correctness

Based on the description, the client can execute at most a single request at a time and then waits for a response. This implies that the client is synchronized with the servers via a pair of messages by default. As long as the servers are correctly synchronized, we will not have issues with the clients. 