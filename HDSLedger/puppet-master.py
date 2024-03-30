#!/usr/bin/env python

import os
import json
import sys
import signal


# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
server_configs = [
    "normal_config",
    "faulty_leader",
    "different_value",
    "multiple_leaders",
    "prepare_value",
    "commit_value",
    "no_commit",
    "node_replay_attack",
    "client_replay_attack",
]

# Client node configuration file name
client_configs = [
    "single_client",
    "multiple_clients",
]

# Extract config file names from the arguments if provided
# Usage: python3 puppet-master.py <server_config> <client_config>
if len(sys.argv) == 3:
    server_config = sys.argv[1]
    client_config = sys.argv[2]
    if server_config not in server_configs or client_config not in client_configs:
        server_config = server_configs[0]
        client_config = client_configs[1]
elif len(sys.argv) == 2:
    server_config = sys.argv[1]
    client_config = client_configs[1]
    if server_config not in server_configs:
        server_config = server_configs[0]
else:
    server_config = server_configs[0]
    client_config = client_configs[1]

# Add the .json extension to the config file names
server_config += ".json"
client_config += ".json"

def quit_handler(*args):
    os.system(f"pkill -i {terminal}")
    #os.system(f"rm KeyInfrastructure/*.class KeyInfrastructure/*.priv KeyInfrastructure/*.pub")
    sys.exit()


# Compile classes
os.system("mvn clean install")
os.system(f"javac KeyInfrastructure/*.java")

# Generate keys for Nodes and Clients
with open(f"Service/src/main/resources/{server_config}") as s:
    with open(f"Client/src/main/resources/{client_config}") as c:
        data_server = json.load(s)
        data_client = json.load(c)
        data_all = data_client + data_server
        os.chdir("KeyInfrastructure")

        for key in data_all:
            privPath = f"./id{key['id']}.key"
            pubPath = f"./id{key['id']}.key.pub"
            if (os.path.exists(privPath) and os.path.exists(pubPath)):
                continue
            os.system(f"java RSAKeyGenerator w {privPath} {pubPath}")

        os.chdir("..")
        print("\nGenerated and saved keys\n")

# Spawn blockchain nodes
with open(f"Service/src/main/resources/{server_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} --title Node:{key['id']} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config} {client_config}' ; sleep 500\"")
            sys.exit()

# Spawn client nodes
with open(f"Client/src/main/resources/{client_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} --title Client:{key['id']} sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']} {client_config} {server_config}' ; sleep 500\"")
            sys.exit()

signal.signal(signal.SIGINT, quit_handler)

while True:
    print("Type quit to quit")
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()
