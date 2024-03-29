# To Do - Markdown
Ledger pode dar overflow, por append requests num buffer caso não tenhamos a anterior
Pagar ao lider que criou o bloco, não ao lider atual, i.e block producer

# Tests
Ate agora passamos nos seguintes testes:
- Leader faulty/byzantino que crasha
- Node byzantino que assume ser leader (multiplos leaders)
- Leader byzantino que altera o bloco de transaçoes requested pelos clientes no inicio do consenso
- Node byzantino que altera o bloco de transaçoes que manda no prepare
- Node byzantino que altera o bloco de transaçoes que manda no commit
- Round change com valores prepared (quando os nodes nao dao commit, por exemplo)
- Cliente byzantino que tenta dar forge de uma transferencia

# Attacks
- Replay attack - protected against using nonces
- Non repudiation - using signatures