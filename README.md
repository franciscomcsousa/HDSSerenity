# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

---

# Configuration Files

### Node configuration

Can be found inside the `resources/` folder of the `Service` module.

```json
{
    "id": <NODE_ID>,
    "isLeader": <IS_LEADER>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "behavior": <BEHAVIOR>,
}
```

## Dependencies

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

## Generate Keys

All the nodes in the system use public and private keys that must be generated before-hand.
To generate the keys, run the following commands inside the `KeyInfrastructure` directory:

```bash
javac *.java
java RSAKeyGenerator w ./<priv-key-name>.priv ./<pub-key-name>.pub
```

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes
of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments, which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs`, which contains the possible configurations for the blockchain nodes
- `client_config` - a string from the array `client_configs`, which contains the possible configurations for the blockchain clients

Run the default script with the following command:

```bash
python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

## Testing

To perform specific tests on the system, two arguments can be added to the default command:

```bash
python3 puppet-master.py <servers_config> <clients_config>
```

- `servers_config` - the configuration file used by the server nodes
- `clients_config` - the configuration file used by the client nodes

**Note: It is not necessary to provide the file extension (.json), only the file name is needed**

If no arguments are used or invalid ones are entered, the script will default to the normal configuration.

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

Run without arguments

```
cd <module>/
mvn compile exec:java
```

Run with arguments

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```
---
This codebase was adapted from last year's project solution, which was kindly provided by the following group: [David Belchior](https://github.com/DavidAkaFunky), [Diogo Santos](https://github.com/DiogoSantoss), [Vasco Correia](https://github.com/Vaascoo). We thank all the group members for sharing their code.

