# Criação de pedidos com catálogo e Outbox

Estado: **aprovada, implementada e validada**, em 28/10/2026. Item 04 do [roadmap](../../docs/implementation-roadmap.md). Após receber esta spec e suas tarefas, o usuário autorizou explicitamente: “implemente”. Tarefas em [tasks.md](tasks.md).

## Objetivo

Receber produtos e quantidades pela API, obter os snapshots no catálogo e persistir pedido, itens e comando `ReserveStock` na mesma transação PostgreSQL. O sucesso significa pedido criado em `AWAITING_STOCK` e comando duravelmente pendente de publicação; não significa estoque reservado.

## Contexto e dependências

- Itens 01, 02 e 03 concluídos: [base](../base-persistencia-integracao/spec.md), [domínio](../dominio-pedidos/spec.md) e [modelo relacional](../modelo-relacional-pedidos/spec.md). Existem `Order`, `OrderItem`, `OutboxMessage`, `ProcessedMessage`, suas migrations V1/V2 e testes PostgreSQL Testcontainers.
- As entidades usam Lombok, getters fluentes, construtores JPA protegidos e criação validada. No início desta etapa não havia controller, serviço de criação ou cliente de catálogo. A implementação acrescentou esses componentes e persistência com `EntityManager`/`TransactionTemplate`, sem repositórios adicionais.
- Produtos, Estoque e Kafka não estão disponíveis. Os contratos abaixo foram **aprovados localmente para esta implementação**, sem acordo ou validação com esses serviços externos. Sua implementação em Pedidos pode ser validada com HTTP simulado e PostgreSQL descartável. Integração real permanece pendente.
- A [arquitetura](../../architecture.md) propõe `OrderController`, `OrderService`, `CatalogClient`, snapshots e envelope. Seu inventário inicial antecede as entidades/migrations já implementadas; as specs e o código atual prevalecem sobre esse inventário.
- Esta etapa não inicia Kafka. O item 05 publicará as linhas produzidas aqui; o item 06 consumirá resultados de reserva.

## Regras de negócio

1. Preservar integralmente o domínio aprovado: ao menos um item, produtos distintos, UUIDs obrigatórios, quantidade inteira positiva, nome histórico não vazio, moeda ISO 4217 única e preços não negativos representáveis exatamente em duas casas. Não somar produtos repetidos ou converter moedas.
2. Nome, preço e moeda vêm exclusivamente do catálogo. A API recebe apenas identificadores e quantidades. Não receber preço, nome, moeda, status ou identificador do pedido do cliente.
3. Proposta: produto ausente ou inativo rejeita toda a solicitação. Uma falha em qualquer consulta também impede a criação; não montar pedido parcial nem usar snapshot anterior como fallback.
4. O pedido recebe UUID gerado no backend e nasce em `AWAITING_STOCK`; total e subtotais usam `BigDecimal` e as operações existentes do domínio, sem arredondamento, impostos, descontos ou frete.
5. Cada criação confirmada produz exatamente um pedido, seus itens e uma linha pendente de Outbox com um comando de reserva contendo todos os produtos/quantidades daquele pedido. Não inserir `processed_messages` na criação.
6. Proposta: esta versão não oferece idempotência de criação. Duas requisições válidas com o mesmo corpo criam pedidos e mensagens distintos. Não adicionar `Idempotency-Key`, deduplicação por conteúdo ou retry automático da operação. Se a resposta se perder depois do commit, repetir a requisição pode duplicar o pedido; a resposta HTTP não deve prometer o contrário.
7. A disponibilidade real é responsabilidade de Estoque, posteriormente. `active=true` no catálogo não garante saldo. Alterações de catálogo após a leitura não alteram o snapshot já persistido; não há transação distribuída ou garantia de snapshot simultâneo entre as consultas de produtos.

## Contratos aprovados para implementação local

### API de criação

`POST /orders`, com `Content-Type: application/json`:

```json
{
  "items": [
    { "productId": "11111111-1111-4111-8111-111111111111", "quantity": 2 },
    { "productId": "22222222-2222-4222-8222-222222222222", "quantity": 3 }
  ]
}
```

- Corpo, `items`, cada item e seus dois campos são obrigatórios. Lista vazia, UUID inválido/nulo, produto repetido ou quantidade fora de `1..2147483647` são inválidos. Rejeitar número fracionário e coerção de string para quantidade; rejeitar campos desconhecidos no request, inclusive `price`, em vez de utilizá-los ou ignorá-los silenciosamente.
- A validação estrutural e de duplicatas precede qualquer chamada ao catálogo e qualquer gravação. Não introduzir limites arbitrários adicionais de itens ou valores nesta etapa.
- Resposta `201 Created`, JSON com somente `id` (UUID do pedido), `status` (`AWAITING_STOCK`), `total` (número decimal com duas casas), `currency` (código ISO 4217) e `createdAt` (data UTC ISO 8601). Gerar DTO próprio, sem serializar entidades JPA ou relações lazy.
- Retornar sucesso somente depois de confirmar o commit. A resposta não inclui endpoint de consulta, resultado de reserva, pagamento ou conteúdo técnico da Outbox.
- Proposta para erros controlados: JSON com `code` e `message` textuais; código estável conforme tabela de erros. A mensagem pode identificar o produto envolvido, sem stack trace, SQL ou corpo bruto da dependência. Não exigir texto humano exato nos testes.
- Esta proposta não adiciona autenticação, usuário/cliente ou propriedade de pedidos, que não estão definidos no domínio. Sua ausência deve ser considerada ao aprovar o contrato; condições de acesso para uma implantação pública precisam de decisão própria. Não presumir JWT, gateway ou serviço de identidade existentes.

### Catálogo de Produtos

Proposta mínima: `GET {catalog.base-url}/products/{productId}` por produto distinto, sequencialmente na ordem recebida e interrompendo na primeira falha. A URL base vem de configuração externa; não apontar por padrão para serviço presumidamente existente. Testes fornecem URL de servidor HTTP simulado. Não implementar catálogo em memória de produção, cache, consulta em lote ou fallback.

Resposta `200 OK`, `application/json`:

```json
{
  "productId": "11111111-1111-4111-8111-111111111111",
  "name": "Produto A",
  "price": 10.25,
  "currency": "BRL",
  "active": true
}
```

- Os cinco campos são obrigatórios e não nulos, com os tipos ilustrados. `productId` deve corresponder ao solicitado; `name` deve ter conteúdo; `price` deve ser número decimal exato não negativo e representável em duas casas; `currency` deve ser código reconhecido pelo domínio; `active` deve ser booleano. Campos adicionais do catálogo podem ser ignorados para permitir evolução aditiva.
- Todos os produtos devem ter a mesma moeda, que passa a ser a moeda do pedido. Moedas individualmente válidas, mas distintas, constituem solicitação não atendível (`422`). Dados inválidos do catálogo constituem erro da dependência (`502`), sem tentar arredondar ou reparar.
- `404` representa produto ausente; `active=false` representa produto indisponível para criação. Demais respostas diferentes de `200`/`404`, JSON inválido, campos ausentes/tipos incompatíveis e identidade divergente seguem a tabela de erros.
- Proposta: conexão limitada a 1 segundo e espera de resposta a 2 segundos, configuráveis por `catalog.connect-timeout` e `catalog.read-timeout`, valores positivos. São limites por consulta, não um prazo total independente da quantidade de itens. Não seguir redirecionamentos, nem fazer retries automáticos nesta etapa.
- `catalog.base-url` deve estar presente e ser uma URL HTTP(S) válida para o cliente; ausência/configuração inválida impede inicialização de forma identificável. Os testes existentes de contexto precisarão receber essa configuração sem depender de Produtos real.

### Envelope de `ReserveStock`

Armazenar uma string JSON UTF-8 lógica no campo textual `outbox_messages.payload`. O JSON inclui os campos da arquitetura:

```json
{
  "messageId": "33333333-3333-4333-8333-333333333333",
  "type": "ReserveStock",
  "version": 1,
  "orderId": "44444444-4444-4444-8444-444444444444",
  "correlationId": "44444444-4444-4444-8444-444444444444",
  "causationId": null,
  "occurredAt": "2026-10-28T15:00:00Z",
  "payload": {
    "items": [
      { "productId": "11111111-1111-4111-8111-111111111111", "quantity": 2 },
      { "productId": "22222222-2222-4222-8222-222222222222", "quantity": 3 }
    ]
  }
}
```

| Campo/registro | Semântica proposta |
| --- | --- |
| `messageId` | UUID gerado uma vez por criação, igual ao `outbox_messages.id`. Deve permanecer no JSON persistido para futura republicação sem regeneração. |
| `type`, `version` | Constantes `ReserveStock` e `1`. Não criar framework genérico de versionamento. |
| `orderId` | UUID do pedido persistido nesta mesma transação. |
| `correlationId` | Igual ao `orderId`, identificando o fluxo deste pedido. |
| `causationId` | Campo presente com `null`: é o comando inicial decorrente de uma requisição HTTP sem mensagem causadora. |
| `occurredAt` | `Instant` atribuído uma vez ao criar o comando; não exigir igualdade com o callback `createdAt` da Outbox ou do pedido. |
| `payload.items` | Produtos e quantidades validados, sem repetição e iguais aos itens persistidos. Não incluir preços, nomes ou dados de pagamento. Não prometer ordem persistida de itens. |
| `topic` | `inventory.commands`. É apenas destino armazenado; nenhum tópico será provisionado ou acessado aqui. |
| `partition_key` | Representação textual canônica do `orderId`. |
| `published_at` | `null` na criação; permanece assim nesta etapa. |

A factory atual `OutboxMessage.create(topic, partitionKey, payload)` gera o UUID internamente depois de receber o payload. Para evitar construção circular, **esta proposta autoriza uma sobrecarga mínima de criação que receba UUID explícito, valide não nulidade e preserve a factory atual**. O serviço gera `messageId`, serializa o envelope e cria a Outbox com esse mesmo UUID; não usar setters, reflexão ou mutação posterior do payload. A factory existente mantém a geração automática para seus chamadores atuais. Não criar coluna ou migration para duplicar o identificador.

A aprovação deste contrato permite implementar e testar o produtor local; não demonstra acordo ou compatibilidade já validada com Estoque real. Mudanças posteriores do contrato exigirão revisão da spec e consideração das mensagens já persistidas.

## Fluxo e transação local

1. Validar o request; obter e validar todos os snapshots HTTP antes de abrir a transação de escrita.
2. Formar o agregado com `Order.create`, gerar a identidade do comando e serializar o envelope com os dados efetivos do pedido. Não fazer consulta HTTP mantendo transação de escrita aberta.
3. Em uma única transação local, persistir pedido, itens via cascade existente e a linha de Outbox. Não usar transação independente para a Outbox, `REQUIRES_NEW`, gravação assíncrona ou evento após commit para criá-la.
4. Qualquer falha de inserção, flush ou commit reverte todas as gravações desta criação; preservar registros previamente existentes. Nenhuma tentativa de reserva é enviada diretamente ao Kafka.
5. Devolver DTO com os dados do pedido após commit. Como Open Session in View está desativado, não depender de acesso lazy no controller.

## Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF01 | Expor a API proposta com validação estrita do request e DTOs explícitos de resposta/erro. |
| RF02 | Consultar Produtos pelo contrato proposto, com configuração de URL/timeouts e validação de snapshots, sem fallback ou repetição automática. |
| RF03 | Construir pedido com dados históricos exclusivamente do catálogo e regras do domínio preservadas; rejeitar ausência, inatividade ou moedas diferentes. |
| RF04 | Gravar pedido, itens e exatamente um comando pendente na mesma transação local, sem gravação parcial em falha. |
| RF05 | Persistir envelope `ReserveStock` versão 1 e metadados da Outbox conforme contrato, incluindo identidade compartilhada e extensão compatível da factory. |
| RF06 | Distinguir erros do request, regras de criação, falhas do catálogo e falhas internas pela tabela de erros, sem expor detalhes internos. |
| RF07 | Implementar a semântica explícita de repetição: requisições válidas repetidas criam pedidos independentes; ausência de idempotência é documentada. |
| RF08 | Adaptar configuração e testes anteriores, documentar contratos, comandos, cobertura real e limites de integração. |

## Requisitos não funcionais

- **RNF01 — Independência externa:** validar a etapa sem Kafka, Estoque ou Produtos reais. HTTP é simulado no limite de rede; banco é PostgreSQL Testcontainers com as migrations reais. Simular somente o método Java do cliente não comprova seu contrato HTTP.
- **RNF02 — Preservação:** não alterar schema, V1/V2, regras de domínio ou contratos atuais das factories. A sobrecarga explícita de Outbox é aditiva e mantém as regressões existentes. Não executar clean/reparo/baseline, nem usar banco local nos testes.
- **RNF03 — Estrutura mínima:** reutilizar entidades e stack Spring existentes. Criar somente controller/DTOs, coordenação da criação, cliente de catálogo e persistência necessários ao caso. Não criar camada genérica de gateways, mapper framework, interfaces por classe, catálogo duplicado ou novo modelo JPA. Usar Lombok quando reduz boilerplate nas classes novas, sem setters em entidades; DTOs podem ser records conforme arquitetura.
- **RNF04 — Limites e precisão:** HTTP fora da transação de escrita, timeouts configurados, valores monetários com `BigDecimal` de ponta a ponta. Não converter para `double`, arredondar ou reutilizar preço do cliente.
- **RNF05 — Evidências:** executar testes ligados aos critérios, build completo e JaCoCo; informar cobertura por unidade nova e distinguir medição unitária/agregada quando aplicável, sem presumir percentuais. Docker indisponível significa validação bloqueada, não sucesso ou uso de banco alternativo.
- **RNF06 — Aprovação e escopo:** implementar somente após aprovação explícita dos contratos e decisões desta spec. Revisão independente somente se solicitada pelo usuário, conforme AGENTS.md. Não iniciar itens 05/06 ou commitar automaticamente.

## Casos de erro e respostas propostas

Nos casos abaixo não confirmar pedido/itens/Outbox desta solicitação. Falhas do catálogo ocorrem antes da transação; falhas de persistência provocam rollback.

| Caso | HTTP | `code` |
| --- | --- | --- |
| JSON/request inválido, campos inesperados, item nulo, UUID/quantidade inválidos, produto repetido | 400 | `INVALID_ORDER_REQUEST` |
| Produto retorna `404` | 422 | `PRODUCT_NOT_FOUND` |
| Produto válido retorna `active=false` | 422 | `PRODUCT_INACTIVE` |
| Produtos com moedas válidas diferentes | 422 | `MIXED_CURRENCIES` |
| Catálogo sem conexão, timeout ou resposta `429`/`5xx` | 503 | `CATALOG_UNAVAILABLE` |
| Catálogo retorna outro status inesperado (inclusive `3xx`, `401`, `403`), corpo inválido ou snapshot inconsistente | 502 | `INVALID_CATALOG_RESPONSE` |
| Falha de serialização, inserção, flush, commit ou erro interno inesperado | 500 | `ORDER_CREATION_FAILED` |
| Tipo de conteúdo não suportado | 415 | `UNSUPPORTED_MEDIA_TYPE` |

Se múltiplas falhas forem possíveis, a validação estrutural tem precedência e a primeira falha na sequência de consultas é reportada. Não agregar erros de produtos por chamadas adicionais. Erro em `500` não revela causa interna; não mapear qualquer `IllegalArgumentException` indiscriminadamente como erro do cliente, pois dados inválidos do catálogo pertencem a `502`.

## Critérios de aceitação

| ID | Cenário e evidência requerida | Referências |
| --- | --- | --- |
| CA01 | `POST /orders` com dois produtos ativos simulados, preços `10.25` e `0.10`, quantidades 2 e 3, mesma moeda, retorna `201`, UUID e `AWAITING_STOCK`, total `20.80`, moeda e criação. Após commit e novo contexto, banco contém um pedido, dois snapshots corretos e uma Outbox pendente; resposta não expõe entidades ou campos internos. | RF01, RF03–RF05 |
| CA02 | Requests inválidos da tabela recebem `400`; incluir lista/item/campos nulos, lista vazia, duplicatas, UUID inválido, quantidade zero/negativa/fracionária/string/fora do int e campos extras de preço/moeda/status. Nenhuma chamada HTTP ou escrita ocorre. Conteúdo incompatível recebe `415`. | RF01, RF06 |
| CA03 | Servidor HTTP simulado verifica `GET /products/{id}`, uma chamada por produto em sucesso, correspondência de IDs, parsing exato de preço e dados recebidos. Testar `404`, inativo, moedas diferentes, JSON inválido, campo obrigatório ausente/nulo, tipo incorreto, ID divergente, nome vazio, moeda inválida, preço negativo/`1.001`, status inesperado, `429`/`5xx`, conexão recusada e atraso além do timeout. Retornos seguem a tabela, não há retries/fallback ou gravação. Preço zero e `1.000` são aceitos sem arredondamento. | RF02–RF03, RF06; RNF01, RNF04 |
| CA04 | Ler e desserializar o JSON efetivamente persistido, verificando todos os campos e tipos, `messageId = outbox.id`, UUID do pedido, correlação, causalidade nula, versão/tipo, instante válido e produtos/quantidades iguais aos itens. Confirmar tópico, chave e publicação nula. Testar sobrecarga validada da factory e preservar o contrato da factory anterior. | RF05; RNF02 |
| CA05 | Em PostgreSQL descartável, provocar falha real na inserção da Outbox depois de inserir e executar flush de pedido/itens dentro do caminho transacional de produção. Após rollback e nova conexão, não existe nenhum dos registros daquela tentativa e registros anteriores continuam intactos. Teste não pode envolver todo o cenário numa transação de teste que esconda commits ou criar outro serviço apenas para simular atomicidade. | RF04; RNF01–RNF02 |
| CA06 | Falha de serialização antes da persistência e falha de persistência/commit não retornam `201`; erro é sanitizado e não deixa gravação parcial. Sucesso é observado após retorno do serviço transacional, não antes do commit. Confirmar que consultas HTTP não mantêm transação de escrita aberta. | RF04, RF06; RNF04 |
| CA07 | Repetir duas vezes o mesmo request válido produz dois UUIDs de pedido, dois `messageId` diferentes e uma Outbox por pedido, com snapshots corretos. Registrar explicitamente a limitação em caso de resposta perdida após commit. | RF07 |
| CA08 | Contextos e testes existentes continuam passando com configuração explícita do catálogo; nenhum contexto inicia Kafka ou acessa serviços reais. URL ausente/inválida ou timeouts não positivos falham com configuração identificável. Configuração de teste não entra como fallback de produção. | RF02, RF08; RNF01–RNF04 |
| CA09 | `bash ./mvnw clean verify` passa, incluindo regressões de domínio, persistência e mensagens, e gera JaCoCo. Registrar resultados, cobertura real das unidades novas, artefato e limites; documentação diferencia simulações de integração real e deixa itens 05/06 pendentes. | RF08; RNF05–RNF06 |

## Fora de escopo

Publicar Kafka, polling/concorrência/retentativas de publicadores, marcar `published_at`, consumir resultados de reserva, deduplicação operacional e offsets, implementar Produtos ou Estoque, provisão de infraestrutura, consulta/listagem de pedidos, cancelamento, pagamento, notificações, autenticação/autorização e propriedade de pedidos, idempotência da API, cache/batch de catálogo, transação distribuída, garantia de disponibilidade de estoque, mudanças de migrations e índices operacionais.

## Decisões aprovadas

O usuário aprovou explicitamente em 28/10/2026: `POST /orders` e respostas propostas; HTTP de catálogo `GET /products/{id}`; rejeição de ausentes/inativos/moedas diferentes; timeouts iniciais 1s/2s; criação sem idempotência; envelope e semântica de IDs/datas; sobrecarga aditiva de Outbox; testes via simulação HTTP e PostgreSQL.

As seguintes decisões compõem a aprovação e exigem revisão desta spec caso sejam alteradas:

- Cada requisição válida cria um pedido novo; idempotência permanece fora do escopo, inclusive quando a resposta se perde após commit.
- Os contratos de Produtos e `ReserveStock` são a base local para testes e implementação. Compatibilidade com futuros serviços reais permanece pendente e mudanças exigem revisão do contrato.
- Autenticação e identificação do comprador permanecem fora do recorte aprovado.

## Validação histórica da preparação

Leitura de AGENTS.md, configuração do arquiteto, roadmap, estado, arquitetura, specs anteriores, entidades/factories, configurações, POM e inventário de testes. Esta entrega altera somente os dois documentos desta funcionalidade. Nenhum build, teste, SQL, migration ou integração foi executado nesta preparação; não há nova medição de cobertura. Os resultados dos itens anteriores permanecem históricos.

## Evidências da implementação — 28/10/2026

Após a aprovação explícita, `bash ./mvnw clean verify` passou com 48 testes, sem falhas, erros ou skips. Foram exercitados contratos HTTP/API, falhas do catálogo, configuração inválida, serialização, repetição, envelope persistido, criação e rollback com PostgreSQL Testcontainers. O teste de rollback força falha real na Outbox após comprovar a inserção de pedido e itens, usando apenas infraestrutura de teste no banco descartável. A integração mantém `ddl-auto=validate` e `open-in-view=false` e verifica que consultas HTTP ocorrem sem transação ativa.

A rodada isolada de 20 testes unitários/slice, sem PostgreSQL, cobre 100% das linhas das novas unidades. Branches: `CatalogClient` 19/20 (95%), `CatalogProperties` 19/20 (95%) e `OrderService` 12/12 (100%). Relatórios em `target/site/jacoco-unit/` e cobertura agregada em `target/site/jacoco/`. A entidade existente `OutboxMessage` tem 100% das linhas na suíte agregada, incluindo callbacks JPA; a rodada isolada cobre 10/12 linhas (83,3%), sem exercitar esses callbacks.

V1/V2 foram preservadas e o JAR contém somente migrations de produção. Nenhum banco local ou serviço externo foi usado; publicação e consumo Kafka permanecem nas etapas 05/06. Configuração, exemplos e limites em [order-creation.md](../../docs/order-creation.md); tarefas e evidências detalhadas em [tasks.md](tasks.md).
