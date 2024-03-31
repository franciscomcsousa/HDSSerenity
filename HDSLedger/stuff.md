# Tests
Ate agora passamos nos seguintes testes:
- Leader faulty/byzantino que crasha
- Node byzantino que assume ser leader (multiplos leaders)
- Leader byzantino que altera o bloco de transaçoes requested pelos clientes no inicio do consenso
- Node byzantino que altera o bloco de transaçoes que manda no prepare
- Node byzantino que altera o bloco de transaçoes que manda no commit
- Round change com valores prepared (quando os nodes nao dao commit, por exemplo)
- Cliente byzantino que tenta dar forge de uma transferencia
- Replay attacks tanto de nodes e clientes
- Leader a ignorar um cliente
- Leader mandar uma big instance no inicio do consenso
- Node byzantino que não recebe msgs dos outros, trigger a um timerExpiry e recebe quorum de commit ja feito pelos outros

# Attacks
- Replay attack - protected against using nonces
- Non repudiation - using signatures

# Random stuff to write in the report
- Leader grants total order of requests
- Rotating leaders will make no transactions be held hostage
  - also grants that f+1 responses to a client transfer is enough
- we chose double as a data type because of its precision
- not forget to say that each id (client and nodes) is associated with a public key