# TODO
Ledger pode dar overflow, por append requests num buffer caso não tenhamos a anterior
Ledger tem que ser sincronizada
Pagar ao lider que criou o bloco, não ao lider atual, i.e block producer

# Perguntas 
- Para onde/para quantos nós deve o cliente mandar o seu request de forma segura?
- Messagens encriptadas por uma chave assimétrica precisam de um nounce para evitar replayability?
- O quão atual tem que ser o check_balance()?
- Pagar % ao lider, é suposto ir rodando de lider?

# Ataques
- cuidado com lideres e clientes bizantinos a trabalhar em conjunto
