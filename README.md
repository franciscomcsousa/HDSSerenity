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

The `clients_config` only has two options:

- `multiple_clients`: the default option, where 3 client applications are created
- `single_client`: where only 1 client application is created

If no arguments are used or invalid ones are entered, the script will default to the normal configuration. If only the server argument is passed, the default configuration of multiple clients will be used.

## Tests

Each test is done through the use of different server configurations. In order to perform a test, just use one of the following test names on the `servers_config` argument of the program.

The description of each test is as follows:

- `faulty_leader`: byzantine leader that crashes after program start
- `multiple_leaders`: byzantine node (node 2) that assumes they are the leader, leading to multiple leaders
- `different_value`: byzantine leader that changes the block of transactions requested by the clients before consensus starts or byzantine client that tries to forge a transaction
- `prepare_value`: byzantine node (node 1) that changes the block of transactions it sends in the prepare message
- `commit_value`: byzantine node (node 3) that changes the block of transactions it sends in the commit message
- `no_commit`: forced round change on the first round of consensus, with prepared values, but without nodes having committed them
- `node_replay_attack`: byzantine node (node 1) that attempts a replay attack on the second consensus instance
- `client_replay_attack`: byzantine client (any of them) that attempts a replay attack
- `ignore_client`: byzantine leader that ignores a client's requests (client 20), which after at most 5 consensus instances when the leader changes, they get processed by the new leader
- `big_instance`: byzantine leader that creates a message with a big instance number (10000) on the second consensus instance
- `commit_quorum`: byzantine node (node 2) that does not receive messages from others on the first consensus instance, triggering a timerExpiry and a round change while the other nodes have committed, to which the other nodes reply with a quorum of commit messages

**Note: all tests require the user to input the client commands themselves and perform transactions as they please**

Each test uses the `behavior` field of the node configurations to determine what to test

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

