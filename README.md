# Quorum Based Total Order Broadcast

This project is aimed at implementing a protocol Total Order Broadcast (or Atomic Broadcast) that
relies on a coordinator and a quorum of nodes. This project is inspired by [Apache ZooKeeper](https://zookeeper.apache.org), a
service for the configuration and coordination of
distributed applications.

There are two main components:
- Two-phase broadcast (with a quorum)
- Coordinator election (on a ring)

![System Overview](images/system_overview.png)

# Project Structure

The project is contained into the ```src``` folder and it is structured as follows:
- `classes` contains the classes that implements clients and cohorts
- `loggers` contains methods to log system state
- `messages` contains all messages class that are exchanged in the system
- `tests` contains the test cases for the system
- `tools` contains general tools

# Requirements

- Java
- Gradle
- JUnit 5
- [dotenv-java](https://github.com/cdimascio/dotenv-java)

# How to run

1. Clone the repository the way you prefer.

```bash
git clone https://github.com/StefanoSacchet/QuorumBasedTotalOrderBroadcast.git
```

2. Assuming you are on the root of the project, create the config file.
   You already have a .env.example to get started with default configuration, so you can just copy it.
```bash
cd config/
cp .env.example .env
```

3. Run the project with your favourite IDE or via java CLI.

# Contributors

1. [Stefano Dal Mas](https://github.com/StefanoDalMas) [stefano.dalmas@studenti.unitn.it]
2. [Stefano Sacchet](https://github.com/StefanoSacchet) [stefano.sacchet@studenti.unitn.it]
