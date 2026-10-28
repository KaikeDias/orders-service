# Roadmap de integração: Pedidos, Kafka, Estoque e Produtos

Proposta de sequência de desenvolvimento a partir das etapas 01–04 de Pedidos, concluídas em 28/10/2026. Este roteiro coordena as dependências entre os componentes; não substitui as specs nem autoriza implementações.

## Qual deve ser o próximo passo?

Recomendo preparar Kafka no ambiente local antes de implementar a integração do serviço de Estoque. O primeiro avanço funcional será publicar no broker os comandos que Pedidos já grava na Outbox.

Antes dessa implementação, revisar o contrato de `ReserveStock` e especificar a publicação. O contrato do comando já foi aprovado localmente em Pedidos; precisa ser adotado explicitamente pelo futuro consumidor de Estoque. Os contratos dos eventos de resultado ainda precisam ser definidos.

O domínio de Estoque pode ser desenvolvido sem Kafka, com testes unitários e de banco. A sequência abaixo prioriza validar um caminho por vez: primeiro a saída de Pedidos, depois a reserva e, por fim, o retorno do resultado.

## Ponto de partida

- Pedidos já recebe `POST /orders`, consulta o catálogo por HTTP e grava pedido, itens e `ReserveStock` na mesma transação PostgreSQL.
- O pedido fica em `AWAITING_STOCK`; a mensagem fica com `published_at` nulo.
- Não há publicador ou consumidor Kafka implementado em Pedidos.
- Produtos, Estoque e a infraestrutura Kafka do projeto ainda não estão disponíveis.
- Os testes atuais usam catálogo HTTP simulado e PostgreSQL Testcontainers. Esses testes não comprovam integração com os serviços externos reais.

## Ordem recomendada

| Passo deste roteiro | Onde trabalhar | Entrega | Condição para avançar |
| --- | --- | --- | --- |
| A — Contratos | Pedidos e planejamento de Estoque | Revisar `ReserveStock`; definir eventos de resultado e responsabilidades | Contratos necessários aprovados e exemplos verificáveis |
| B — Kafka local | Infraestrutura de desenvolvimento | Broker reproduzível e configuração de acesso | Produzir e consumir mensagem de teste no ambiente local |
| C — Publicador da Outbox | Pedidos, item 05 | Enviar comandos persistidos e registrar confirmação | Testes de envio, indisponibilidade e republicação aprovados |
| D — Domínio e banco de Estoque | Serviço de Estoque | Saldos, reservas e regras de concorrência/idempotência | Reserva não excede saldo disponível nem repete efeito |
| E — Integração Kafka de Estoque | Serviço de Estoque | Consumir comando e publicar resultado de forma confiável | Comando real produz evento de reserva aceita ou recusada |
| F — Resultado em Pedidos | Pedidos, item 06 | Consumir resultados e atualizar estado com deduplicação | Pedido muda uma vez, com rollback e recuperação validados |
| G — Consulta e fluxo integrado | Pedidos, item 07, e ambiente integrado | Consulta e cenários de ponta a ponta | Reserva aceita/recusada observável pela API de consulta |
| H — Cancelamento e pagamento | Pedidos e Estoque | Itens 08/09 e contratos correspondentes de Estoque | Regras e compensações especificadas antes de implementar |

O serviço de Produtos pode ser desenvolvido em paralelo aos passos B–F. O marco G com serviços reais depende também dele; até sua disponibilização, os testes de criação continuam usando HTTP simulado e devem registrar essa limitação.

## A — Alinhar contratos antes da integração

Partir do [contrato de criação aprovado](../specs/criacao-pedidos/spec.md), preservando o que já está gravado na Outbox:

- Comando `ReserveStock`, versão 1, destino `inventory.commands` e chave `orderId`.
- Envelope com `messageId`, `type`, `version`, `orderId`, `correlationId`, `causationId`, `occurredAt` e `payload`.
- Identidade da mensagem igual à identidade da Outbox e preservada em republicações.
- Payload com produtos e quantidades; preços e nomes continuam sob responsabilidade do catálogo e dos snapshots do pedido.

Definir os payloads de `StockReserved` e `StockReservationRejected`, a identificação da reserva e as regras para versões, duplicatas e mensagens inválidas. O roadmap prevê os resultados em `inventory.events`, mas seus contratos completos ainda não estão aprovados.

Os identificadores dos eventos de resultado precisam ter semântica explícita: não presumir que sejam o mesmo `messageId` do comando. Definir como correlação e causalidade ligarão o comando aos resultados.

Mudanças que afetem o produtor existente exigem revisão da spec e consideração das mensagens já persistidas. Não criar uma biblioteca compartilhada ou novo serviço de contratos sem necessidade demonstrada.

## B — Disponibilizar Kafka local

Preparar uma configuração reproduzível de desenvolvimento, por exemplo via Docker Compose, com endereço acessível pelas aplicações e procedimento documentado de inicialização e parada. A escolha de versão e configuração pertence à especificação dessa infraestrutura.

O recorte deve cobrir o broker, os tópicos necessários, as configurações dos clientes e uma verificação simples de produção/consumo. Não incluir interface administrativa, cluster de produção ou infraestrutura adicional por antecipação.

Manter separados os objetivos de validação:

- Kafka local permite executar os serviços juntos e inspecionar o fluxo manualmente.
- Kafka via Testcontainers permite testes automatizados isolados, conforme a spec da etapa 05.

O desenvolvimento do publicador não depende de um cluster de produção nem de Estoque pronto. Os testes podem usar um consumidor de teste para verificar os comandos publicados; isso não demonstra reserva de estoque real.

## C — Implementar a etapa 05 de Pedidos

Elaborar `specs/publicacao-outbox/spec.md` e `tasks.md` e aguardar aprovação antes da implementação.

Definir busca de pendências, concorrência entre publicadores, confirmação de envio, novas tentativas e retenção. O publicador deverá enviar o envelope persistido e só registrar `published_at` após confirmação do Kafka.

Os cenários essenciais de validação são:

1. Um pedido criado gera comando consumível em `inventory.commands`, com chave e conteúdo corretos.
2. Kafka indisponível não causa perda da mensagem pendente.
3. Falha entre envio e marcação permite recuperação e possível republicação com o mesmo `messageId`.

Esse marco termina quando Pedidos consegue entregar o comando ao broker. O pedido continua aguardando o resultado da reserva.

## D — Construir o núcleo de Estoque

Criar o serviço de Estoque em seu próprio projeto, com specs por funcionalidade e banco sob sua responsabilidade. Não compartilhar tabelas ou transações de banco com Pedidos.

As specs de Estoque precisam resolver, antes de implementar:

- Como saldo e quantidade reservada são representados e abastecidos.
- Como uma reserva identifica pedido, produtos e quantidades.
- Se a reserva de vários produtos é integral ou admite resultado parcial. Recomenda-se propor reserva integral para o primeiro fluxo, cujo domínio de Pedidos representa aceitação ou recusa do pedido inteiro; essa regra ainda precisa de aprovação em Estoque.
- Como impedir que pedidos concorrentes reservem mais unidades que o disponível.
- Como tratar comando repetido sem reservar saldo novamente.
- Como distinguir mensagem duplicada de uma segunda solicitação conflitante para o mesmo pedido.

Começar com persistência e testes dessas regras. Usar dados controlados para montar os cenários, sem inventar um contrato de abastecimento ou uma integração com Produtos que ainda não tenha sido especificada.

## E — Ligar Estoque ao Kafka

Especificar o consumidor de `ReserveStock` e o produtor dos resultados. O processamento precisa coordenar reserva, deduplicação e registro durável da resposta.

Recomenda-se avaliar uma Outbox própria de Estoque para gravar a reserva e a intenção de publicar o resultado na mesma transação local. Essa é uma proposta para a spec do serviço, não uma funcionalidade já implementada.

Validar saldo suficiente, saldo insuficiente, comandos repetidos, concorrência e recuperação após falhas. Uma duplicata não deve reservar unidades novamente nem deixar Pedidos sem possibilidade de receber o resultado.

Somente após esse passo haverá evidência de que um comando entregue ao broker é efetivamente processado por Estoque.

## F — Implementar a etapa 06 de Pedidos

Com os eventos definidos, elaborar a spec de `resultado-reserva-estoque` e aguardar aprovação. Os testes podem usar eventos produzidos por um produtor de teste antes da disponibilização de Estoque; a validação integrada continua pendente nesse caso.

O plano de Pedidos é consumir os resultados, registrar `(consumer, message_id)` em `processed_messages` e alterar o pedido na mesma transação local. O offset deve ser confirmado após a persistência.

| Resultado | Estado inicial | Estado esperado |
| --- | --- | --- |
| `StockReserved` | `AWAITING_STOCK` | `AWAITING_PAYMENT` |
| `StockReservationRejected` | `AWAITING_STOCK` | `REJECTED` |

Testar repetição concorrente, rollback, conflito otimista, pedido inexistente e evento incompatível. A simples existência da tabela de processamento não resolve essas políticas.

## G — Completar o primeiro fluxo observável

Implementar a etapa 07 de Pedidos após sua própria especificação: consultar o pedido sem presumir rota, listagem ou condições de acesso ainda não definidas.

Para encerrar o primeiro marco integrado com os serviços reais, disponibilizar também Produtos com o contrato HTTP aprovado e verificar:

1. Catálogo fornece snapshots reais; Pedidos cria o pedido e a Outbox.
2. Publicador envia `ReserveStock` ao Kafka.
3. Estoque processa a reserva e publica o resultado.
4. Pedidos consome o resultado e confirma a alteração no banco.
5. A consulta apresenta `AWAITING_PAYMENT` ou `REJECTED`.

Executar ambos os caminhos de reserva e cenários de repetição/recuperação. Resultados obtidos com algum participante simulado devem identificar esse participante, sem declarar o marco real completo.

## H — Evoluir para cancelamento e pagamento

Seguir a ordem atual do roadmap de Pedidos:

- Etapa 08: cancelamento, condições de liberação e corridas com o resultado da reserva; coordenar os contratos correspondentes com Estoque.
- Etapa 09: pagamento Stripe e confirmação definitiva do estoque, incluindo falhas e compensações. Decidir também o ciclo de vida da reserva enquanto o pagamento está pendente.
- Etapa 10: notificações internas de Pedidos, conforme os acontecimentos e canais aprovados.

`AWAITING_PAYMENT` será o fim do primeiro fluxo de reserva aceito, não evidência de cobrança ou pagamento implementado. Expiração de reserva, liberação, confirmação e compensações ainda precisam de decisões próprias.

## Próxima ação concreta

Iniciar as especificações da infraestrutura Kafka local e da etapa 05 de Pedidos, em recortes separados, usando o contrato de comando já existente. Depois da aprovação de cada recorte, implementar e validar o envio antes de integrar Estoque.

Este documento é planejamento. Não foram criados broker, tópicos, serviços externos, specs dessas funcionalidades ou código nesta tarefa. A validação foi documental, contra o [roadmap de Pedidos](implementation-roadmap.md), o [fluxo da aplicação](application-flow.md) e o [contrato de criação](order-creation.md).
