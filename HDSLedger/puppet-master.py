#!/usr/bin/env python

import os
import json
import sys
import signal


# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
server_configs = [
    "node_config.json",
]

# Client node configuration file name
client_configs = [
    "client_config.json",
]


server_config = server_configs[0]
client_config = client_configs[0]

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
        os.chdir("KeyInfrastructure")
        for key in data_server:
            privPath = f"./node{key['id']}_privKey.priv"
            pubPath = f"./node{key['id']}_pubKey.pub"
            if (os.path.exists(privPath) and os.path.exists(pubPath)):
                continue

            os.system(f"java RSAKeyGenerator w {privPath} {pubPath}")
        for key in data_client:
            privPath = f"./client{key['id']}_privKey.priv "
            pubPath = f"./client{key['id']}_pubKey.pub"
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
                f"{terminal} --title Node:{key['id']} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config}' ; sleep 500\"")
            sys.exit()

# Spawn client nodes
with open(f"Client/src/main/resources/{client_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} --title Client:{key['id']} sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']} {client_config}' ; sleep 500\"")
            sys.exit()

signal.signal(signal.SIGINT, quit_handler)

while True:
    print("Type quit to quit")
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()
