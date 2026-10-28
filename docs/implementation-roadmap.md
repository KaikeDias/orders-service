# Tasks em ordem — microsserviço de Pedidos

Estado: **itens 01 e 02 concluídos; modelo relacional parcialmente implementado**, em 27/10/2026. Evidências de execução estão no [estado do projeto](project-status.md) e nas tarefas de cada funcionalidade.

Base: [diagrama do banco](diagrams/database.md), [arquitetura proposta](../architecture.md), [estado do projeto](project-status.md) e [processo SDD](../AGENTS.md). O código contém a base de persistência e o domínio de pedidos. O inventário antigo da arquitetura não representa essas implementações posteriores.

Este documento organiza a ordem de trabalho e as dependências. Cada item terá seu próprio ciclo SDD e seus próprios `spec.md` e `tasks.md` em `specs/<funcionalidade-kebab-case>/`, quando iniciado. Os nomes de pasta abaixo são sugestões. As entregas e validações são propostas para a futura especificação.

## Disponibilidade das dependências externas

Conforme informado pelo usuário, **os microsserviços de Produtos e Estoque e a infraestrutura Kafka ainda não estão implementados**. As integrações descritas neste roteiro são planejadas; não se deve presumir que esses serviços ou o broker estejam disponíveis.

O desenvolvimento de Pedidos pode usar simulações de HTTP e eventos, após definição e aprovação dos respectivos contratos no ciclo SDD. Kafka via Testcontainers pode ser usado nos testes previstos, quando esse ambiente estiver disponível; isso não significa que a infraestrutura Kafka do projeto esteja implantada.

Se faltar um contrato ou decisão necessária, interromper o trecho dependente e solicitar esclarecimento. A validação com os serviços reais e o Kafka do ambiente integrado permanece pendente até sua disponibilização. Simulações e testes com containers não devem ser apresentados como evidência dessa integração real.

## Processo obrigatório para cada task

1. `spec_architect`: analisar o recorte, esclarecer regras pendentes e elaborar a especificação e as tarefas da funcionalidade, com critérios de aceitação verificáveis.
2. Apresentar documentos, dúvidas e decisões ao usuário; aguardar sua **aprovação explícita antes de implementar**.
3. `software_engineer`: implementar apenas o escopo aprovado e os testes ligados aos critérios de aceitação.
4. `code_reviewer`: revisar; devolver problemas ao engenheiro e repetir a revisão após as correções.
5. Registrar arquivos alterados, testes efetivamente executados, cobertura verificada, riscos e pendências. Concluir somente com evidências.

Executar na ordem abaixo e aguardar a conclusão das dependências. Uma aprovação não autoriza automaticamente o item seguinte. Testes e validação integram cada funcionalidade; mudanças de comportamento retornam à especificação e à aprovação.

## Ordem proposta

| Ordem | Funcionalidade / pasta sugerida | Dependências | Recorte |
| --- | --- | --- | --- |
| 01 | `base-persistencia-integracao` — concluída | Nenhuma | PostgreSQL, JPA, Flyway e suporte de testes |
| 02 | `dominio-pedidos` — concluída | 01 | Pedido, itens, cálculos e estados da reserva |
| 03 | `modelo-relacional-pedidos` — parcial | 01, 02 | Pedido/itens validados; Outbox e processamento pendentes |
| 04 | `criacao-pedidos` | 02, 03 | API, catálogo e gravação atômica com Outbox |
| 05 | `publicacao-outbox` | 04 | Publicar comandos de reserva no Kafka |
| 06 | `resultado-reserva-estoque` | 05 | Consumir resultados com deduplicação e transações |
| 07 | `consulta-pedidos` | 04, 06 | Expor o estado persistido e validar o fluxo inicial |
| 08 | `cancelamento-pedidos` | 05, 06, 07 | Cancelar e coordenar liberação de estoque |
| 09 | `pagamento-confirmacao-estoque` | 07, 08 | Evolução futura: Stripe e confirmação definitiva |
| 10 | `notificacoes-pedidos` | Etapas produtoras dos eventos escolhidos | Evolução futura: notificações internas de Pedidos |

## 01 — Base de persistência e integração

- **Objetivo:** preparar o mínimo necessário para persistência PostgreSQL, migrations e testes de integração do fluxo proposto.
- **Estado:** [spec aprovada e validada](../specs/base-persistencia-integracao/spec.md), com build, testes de migrations e inicialização no banco local demonstrados.
- **Ordem:** antecede as funcionalidades que dependem de persistência; alterações Kafka específicas ficam nos itens 05 e 06.
- **Entrega:** JPA, configuração por ambiente, PostgreSQL Testcontainers versionado, Flyway e JaCoCo. Schemas de negócio não foram criados nesta etapa.

## 02 — Domínio de pedidos

- **Objetivo:** definir pedido e itens, cópia histórica dos dados de produto, totais e transições do primeiro fluxo de reserva.
- **Ordem:** após 01 e antes da criação; cancelamento e pagamento terão especificações próprias.
- **Questões para a spec:** escala/arredondamento, moedas diferentes, produtos repetidos, invariantes dos itens e transições válidas/inválidas entre `AWAITING_STOCK`, `AWAITING_PAYMENT` e `REJECTED`.
- **Entrega/validação proposta:** modelo e testes unitários dos cálculos, invariantes e transições aprovados, incluindo rejeição das operações inválidas.
- **Estado:** [spec implementada e validada](../specs/dominio-pedidos/spec.md). Entidades e enum estão separados em `domain.entity` e `domain.enums`; o mapeamento JPA de pedido/itens foi acrescentado no recorte autorizado do item 03.

## 03 — Modelo relacional de pedidos

- **Objetivo:** alinhar schema e mapeamentos ao [diagrama do banco](diagrams/database.md), com `orders`, `order_items`, `outbox_messages` e `processed_messages`.
- **Ordem:** após 01 e 02, antes de qualquer fluxo que utilize essas tabelas.
- **Escopo proposto:** migrations de produção, mapeamentos JPA, reidratação de pedidos e testes PostgreSQL das restrições físicas e da versão otimista. Nome e preço históricos permanecem nos itens; moeda pertence ao pedido; subtotal e total continuam derivados.
- **Decisões para a spec:** UUID de item, geração de timestamps, versão otimista, precisão monetária sem arredondamento SQL silencioso, reidratação, nulabilidade, FK, unicidade de produto por pedido, cascata e ordem dos itens. A chave de deduplicação é composta por consumidor e mensagem; Outbox armazena envelope serializado em texto, com publicação pendente indicada por `published_at` nulo.
- **Entrega/validação proposta:** migrations e JPA concordantes com o diagrama aprovado, round-trip sem perda de snapshots ou valores, rejeição de violações das restrições e detecção de conflito otimista. Não inclui API, consulta ao catálogo, publicação/consumo Kafka, polling ou deduplicação operacional.
- **Estado:** [spec](../specs/modelo-relacional-pedidos/spec.md) parcialmente autorizada: `OrderItem` JPA e suporte de pedido/itens implementados e validados. Outbox e processamento continuam pendentes de aprovação e implementação, conforme as [tarefas](../specs/modelo-relacional-pedidos/tasks.md). O item completo ainda não está concluído.

## 04 — Criação de pedidos com catálogo e Outbox

- **Objetivo:** receber produtos/quantidades, consultar Produtos e persistir pedido, itens e comando `ReserveStock` na mesma transação local.
- **Ordem:** depende de 02 e 03; utiliza os modelos relacionais existentes e fornece as mensagens pendentes consumidas pelo publicador do item 05.
- **Questões para a spec:** contrato HTTP, erros, produto ausente/inativo, falhas/timeouts do catálogo, repetição da requisição de criação e contrato do comando com Estoque; envelope, identificadores, versão e payload precisam ser acordados.
- **Entrega/validação proposta:** criação em `AWAITING_STOCK`, preços obtidos no backend e snapshots preservados; testes de API/catálogo e integração comprovando persistência atômica e rollback de pedido, itens e Outbox.

## 05 — Publicação da Outbox no Kafka

- **Objetivo:** publicar `ReserveStock` pendente em `inventory.commands`, com chave `orderId`, e marcar publicação após confirmação do Kafka.
- **Ordem:** depende dos registros e contratos do item 04; prepara o envio necessário ao resultado da reserva.
- **Questões para a spec:** polling, concorrência de publicadores, configuração Kafka, falhas, novas tentativas, retenção e preservação do `messageId` em republicações.
- **Entrega/validação proposta:** testes com PostgreSQL/Kafka para publicação confirmada, indisponibilidade e falha entre envio e marcação, demonstrando a política aprovada de recuperação e possível republicação.

## 06 — Resultado da reserva de estoque

- **Objetivo:** consumir `StockReserved` e `StockReservationRejected` de `inventory.events`, aplicar transições e deduplicar por consumidor/mensagem.
- **Ordem:** depende de 05 e das regras do item 02; utiliza `processed_messages` do item 03 e exige acordo dos contratos de eventos com Estoque.
- **Questões para a spec:** payloads/versões, mensagens inválidas ou atrasadas, pedido inexistente, estado incompatível, conflitos otimistas, reprocessamento e falhas permanentes.
- **Entrega/validação proposta:** deduplicação e transição na mesma transação local; offset confirmado após persistência. Testar aceitação/recusa, duplicatas concorrentes, rollback e recuperação após falha sem repetir efeitos.

## 07 — Consulta de pedidos e validação do primeiro fluxo

- **Objetivo:** consultar o pedido e apresentar sua representação pública com o estado persistido.
- **Ordem:** a consulta depende de 04; a validação integrada dos resultados depende também de 06, justificando sua posição na sequência.
- **Questões para a spec:** rota, representação pública, pedido inexistente e condições de acesso; não presumir listagem ou filtros.
- **Entrega/validação proposta:** testes de consulta e erros; cenário integrado de criação, publicação e consumo para reserva aceita e recusada, com consulta do resultado. Integrar com contratos externos acordados ou simulados, explicitando o limite da evidência.

O marco inicial termina aqui, em `AWAITING_PAYMENT` ou `REJECTED`. Chegar a `AWAITING_PAYMENT` não significa que pagamento foi implementado. Os testes anteriores continuam pertencendo às suas respectivas funcionalidades.

## 08 — Cancelamento de pedidos e liberação de estoque

- **Objetivo:** definir solicitação de cancelamento, eventual `ReleaseStock` e consumo de `StockReleased`, usando a infraestrutura já validada.
- **Ordem:** depende de 05, 06 e 07; as regras precisam ser resolvidas antes de ampliar o ciclo para pagamento.
- **Questões para a spec:** estados de origem permitidos, cancelamento antes/depois da reserva ou após rejeição, significado de `CANCELLING`/`CANCELLED`, comandos necessários e corrida com `StockReserved`.
- **Entrega/validação proposta:** contrato de cancelamento e transições aprovados; testes de liberação, duplicatas, falhas e corridas. Não assumir cancelamento concluído antes da condição que a spec definir.

## 09 — Evolução futura: pagamento e confirmação definitiva de estoque

- **Objetivo:** especificar a integração Stripe e sua coordenação com `CommitStock`, `StockCommitted` e `StockCommitRejected` dentro de Pedidos.
- **Ordem:** após 07 e 08; avaliar o tamanho do recorte na análise SDD e, se necessário, desdobrá-lo em funcionalidades sequenciais antes de implementar.
- **Questões para a spec:** contrato Stripe, estados de pagamento, idempotência, autenticação/validação de callbacks, falhas, compensações e interação com cancelamento. Essas regras ainda não estão definidas na arquitetura.
- **Entrega/validação proposta:** contratos e estados aprovados, testes dos cenários de sucesso, repetição e falha acordados, com limites claros para as evidências de integração externa.

## 10 — Evolução futura: notificações

- **Objetivo:** especificar notificações como funcionalidade interna de Pedidos.
- **Ordem:** manter após o pagamento na sequência proposta; sua dependência efetiva será das funcionalidades que produzirem os eventos escolhidos na spec.
- **Questões para a spec:** quais acontecimentos notificam, destinatários, canais, conteúdo, provedor, repetição e falhas; nenhuma dessas escolhas está definida.
- **Entrega/validação proposta:** comportamento aprovado e testes de disparo, duplicidade e falhas conforme os contratos escolhidos, sem criar outro microsserviço.

## Estado desta entrega

Os itens 01 e 02 estão implementados e validados. Este roteiro foi atualizado para inserir o modelo relacional do diagrama como item 03 e ajustar as dependências seguintes. Essa atualização documental não executa migrations nem aprova automaticamente a nova persistência.
