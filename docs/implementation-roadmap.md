# Tasks em ordem — microsserviço de Pedidos

Estado: **planejamento**, em 27/10/2026. Nenhuma especificação aprovada; nenhuma tarefa abaixo implementada ou concluída nesta entrega.

Base: [arquitetura proposta](../architecture.md), [estado do projeto](project-status.md) e [processo SDD](../AGENTS.md). O código atual contém somente a base Spring Boot e suporte de testes, conforme o inventário da arquitetura.

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
| 01 | `base-persistencia-integracao` | Nenhuma | Fundamentos necessários ao primeiro fluxo |
| 02 | `dominio-pedidos` | 01 | Pedido, itens, cálculos e estados da reserva |
| 03 | `criacao-pedidos` | 01, 02 | API, catálogo e gravação atômica com Outbox |
| 04 | `publicacao-outbox` | 03 | Publicar comandos de reserva no Kafka |
| 05 | `resultado-reserva-estoque` | 04 | Consumir resultados com deduplicação e transações |
| 06 | `consulta-pedidos` | 03, 05 | Expor o estado persistido e validar o fluxo inicial |
| 07 | `cancelamento-pedidos` | 04, 05, 06 | Cancelar e coordenar liberação de estoque |
| 08 | `pagamento-confirmacao-estoque` | 06, 07 | Evolução futura: Stripe e confirmação definitiva |
| 09 | `notificacoes-pedidos` | Etapas produtoras dos eventos escolhidos | Evolução futura: notificações internas de Pedidos |

## 01 — Base de persistência e integração

- **Objetivo:** preparar o mínimo necessário para persistência PostgreSQL, migrations e testes de integração do fluxo proposto.
- **Ordem:** antecede as funcionalidades que dependem de persistência; alterações Kafka específicas ficam nos itens 04 e 05.
- **Questões para a spec:** adoção de Spring Data JPA ausente no POM, compatibilidade da stack declarada, conexão/configuração por ambiente, reprodutibilidade dos containers e ferramenta de cobertura.
- **Entrega/validação proposta:** aplicação inicializável no ambiente definido, conexão com PostgreSQL e execução de migrations verificadas; build e suporte de testes demonstrados. Esquemas de negócio pertencem às funcionalidades que os utilizam.

## 02 — Domínio de pedidos

- **Objetivo:** definir pedido e itens, cópia histórica dos dados de produto, totais e transições do primeiro fluxo de reserva.
- **Ordem:** após 01 e antes da criação; cancelamento e pagamento terão especificações próprias.
- **Questões para a spec:** escala/arredondamento, moedas diferentes, produtos repetidos, invariantes dos itens e transições válidas/inválidas entre `AWAITING_STOCK`, `AWAITING_PAYMENT` e `REJECTED`.
- **Entrega/validação proposta:** modelo e testes unitários dos cálculos, invariantes e transições aprovados, incluindo rejeição das operações inválidas.

## 03 — Criação de pedidos com catálogo e Outbox

- **Objetivo:** receber produtos/quantidades, consultar Produtos e persistir pedido, itens e comando `ReserveStock` na mesma transação local.
- **Ordem:** depende de 01 e 02; fornece as mensagens pendentes consumidas pelo publicador do item 04.
- **Questões para a spec:** contrato HTTP, erros, produto ausente/inativo, falhas/timeouts do catálogo, repetição da requisição de criação e contrato do comando com Estoque; envelope, identificadores, versão e payload precisam ser acordados.
- **Entrega/validação proposta:** criação em `AWAITING_STOCK`, preços obtidos no backend e snapshots preservados; testes de API/catálogo e integração comprovando persistência atômica e rollback de pedido, itens e Outbox.

## 04 — Publicação da Outbox no Kafka

- **Objetivo:** publicar `ReserveStock` pendente em `inventory.commands`, com chave `orderId`, e marcar publicação após confirmação do Kafka.
- **Ordem:** depende dos registros e contratos do item 03; prepara o envio necessário ao resultado da reserva.
- **Questões para a spec:** polling, concorrência de publicadores, configuração Kafka, falhas, novas tentativas, retenção e preservação do `messageId` em republicações.
- **Entrega/validação proposta:** testes com PostgreSQL/Kafka para publicação confirmada, indisponibilidade e falha entre envio e marcação, demonstrando a política aprovada de recuperação e possível republicação.

## 05 — Resultado da reserva de estoque

- **Objetivo:** consumir `StockReserved` e `StockReservationRejected` de `inventory.events`, aplicar transições e deduplicar por consumidor/mensagem.
- **Ordem:** depende de 04 e das regras do item 02; exige acordo dos contratos de eventos com Estoque.
- **Questões para a spec:** payloads/versões, mensagens inválidas ou atrasadas, pedido inexistente, estado incompatível, conflitos otimistas, reprocessamento e falhas permanentes.
- **Entrega/validação proposta:** deduplicação e transição na mesma transação local; offset confirmado após persistência. Testar aceitação/recusa, duplicatas concorrentes, rollback e recuperação após falha sem repetir efeitos.

## 06 — Consulta de pedidos e validação do primeiro fluxo

- **Objetivo:** consultar o pedido e apresentar sua representação pública com o estado persistido.
- **Ordem:** a consulta depende de 03; a validação integrada dos resultados depende também de 05, justificando sua posição na sequência.
- **Questões para a spec:** rota, representação pública, pedido inexistente e condições de acesso; não presumir listagem ou filtros.
- **Entrega/validação proposta:** testes de consulta e erros; cenário integrado de criação, publicação e consumo para reserva aceita e recusada, com consulta do resultado. Integrar com contratos externos acordados ou simulados, explicitando o limite da evidência.

O marco inicial termina aqui, em `AWAITING_PAYMENT` ou `REJECTED`. Chegar a `AWAITING_PAYMENT` não significa que pagamento foi implementado. Os testes anteriores continuam pertencendo às suas respectivas funcionalidades.

## 07 — Cancelamento de pedidos e liberação de estoque

- **Objetivo:** definir solicitação de cancelamento, eventual `ReleaseStock` e consumo de `StockReleased`, usando a infraestrutura já validada.
- **Ordem:** depende de 04, 05 e 06; as regras precisam ser resolvidas antes de ampliar o ciclo para pagamento.
- **Questões para a spec:** estados de origem permitidos, cancelamento antes/depois da reserva ou após rejeição, significado de `CANCELLING`/`CANCELLED`, comandos necessários e corrida com `StockReserved`.
- **Entrega/validação proposta:** contrato de cancelamento e transições aprovados; testes de liberação, duplicatas, falhas e corridas. Não assumir cancelamento concluído antes da condição que a spec definir.

## 08 — Evolução futura: pagamento e confirmação definitiva de estoque

- **Objetivo:** especificar a integração Stripe e sua coordenação com `CommitStock`, `StockCommitted` e `StockCommitRejected` dentro de Pedidos.
- **Ordem:** após 06 e 07; avaliar o tamanho do recorte na análise SDD e, se necessário, desdobrá-lo em funcionalidades sequenciais antes de implementar.
- **Questões para a spec:** contrato Stripe, estados de pagamento, idempotência, autenticação/validação de callbacks, falhas, compensações e interação com cancelamento. Essas regras ainda não estão definidas na arquitetura.
- **Entrega/validação proposta:** contratos e estados aprovados, testes dos cenários de sucesso, repetição e falha acordados, com limites claros para as evidências de integração externa.

## 09 — Evolução futura: notificações

- **Objetivo:** especificar notificações como funcionalidade interna de Pedidos.
- **Ordem:** manter após o pagamento na sequência proposta; sua dependência efetiva será das funcionalidades que produzirem os eventos escolhidos na spec.
- **Questões para a spec:** quais acontecimentos notificam, destinatários, canais, conteúdo, provedor, repetição e falhas; nenhuma dessas escolhas está definida.
- **Entrega/validação proposta:** comportamento aprovado e testes de disparo, duplicidade e falhas conforme os contratos escolhidos, sem criar outro microsserviço.

## Estado desta entrega

Somente este roteiro foi criado. Nenhum ciclo SDD foi iniciado, nenhuma funcionalidade foi aprovada e nenhuma implementação ou teste foi executado nesta entrega. A primeira task a entrar no processo é **01 — Base de persistência e integração**.
