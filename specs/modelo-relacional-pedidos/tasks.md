# Tarefas — Modelo relacional de pedidos

Estado: **item 03 aprovado, implementado e validado em 28/10/2026**. O usuário aprovou o restante da [spec](spec.md) com “implemente”. Evidências históricas e da conclusão registradas abaixo.

## Aprovação e análise

- [x] T01A — Registrar a autorização parcial: o usuário solicitou “transforme o orderItem em uma entidade jpa agora, e use lombok para as anotacoes”; autorizado `OrderItem` e suporte indispensável de `Order`, vínculo, duas tabelas, reidratação e testes. Referências: seção de aprovação parcial da spec e RNF05.
- [x] T01B — Obter aprovação separada antes de criar tabelas ou mapeamentos de Outbox e processamento. Referências: decisões 17–21, CA01B, CA06, CA09 e RNF05. A preparação documental não autorizava implementação; a autorização explícita posterior foi “implemente”, em 28/10/2026.
- [x] T02 — Conferir alterações existentes, migrations, testes e referências ao domínio; preservar a refatoração e arquivos locais/staged preexistentes. Confirmar as dependências 01 e 02 sem alterar suas evidências históricas. Referências: RNF01, RNF03.

## Implementação após aprovação

- [x] T03A — Criar migration Flyway somente de `orders` e `order_items` com nomes/tipos do diagrama, campos obrigatórios, PKs, FK, unique e checks, incluindo rejeição de escala excessiva e valores não finitos sem coerção monetária. Referências: CA01A, CA04, CA07.
- [x] T03B — **Após T01B:** criar `V2__create_outbox_and_processed_messages.sql` com as duas tabelas, PKs e nulabilidade do diagrama. Preservar V1, pedido/itens e dados existentes; não adicionar FKs, índices de polling, defaults ou constraints de conteúdo. Referências: decisão 17, CA01B, CA09.
- [x] T04 — Mapear `Order`, converter `OrderItem` para classe JPA e preservar construção validada, cópia dos snapshots, acessores e igualdade de negócio; usar Lombok sem setters ou construtores públicos sem validação. Referências: CA02–CA03.
- [x] T05 — Mapear moeda somente no pedido, relação com itens, cálculo derivado, UUIDs próprios e reidratação sem redefinir estado ou IDs. Não adicionar coluna de posição. Referências: CA02–CA03, CA07.
- [x] T06 — Mapear datas `Instant`, callbacks e `@Version`; configurar cascade apenas de persistência e nenhuma remoção em cascata/orphan removal. Referências: CA05, CA07.
- [x] T07A — **Após T01B:** mapear `OutboxMessage` com getters Lombok fluentes, construtor JPA protegido, criação explícita validada, UUID gerado na aplicação, callback de criação e `publishedAt` inicialmente nulo. Preservar campos imutáveis e não adicionar operação de publicação. Referências: decisões 18–19 e 21, CA06, CA09.
- [x] T07B — **Após T01B:** mapear `ProcessedMessage` e `ProcessedMessageId` (`@EmbeddedId`/`@Embeddable`), Lombok para acessores/construtores protegidos/igualdade da chave, criação validada com UUID recebido e timestamp de inserção. Não expor mutação pública da identidade. Referências: decisões 18, 20–21, CA06, CA09.

## Testes e validação

- [x] T08 — Adaptar a suíte da base para executar migrations reais de pedido/itens no PostgreSQL descartável, mantendo recursos exclusivos de falha fora do artefato e sem colisões de versão. Verificar histórico e reexecução. Referências: CA01A, CA08.
- [x] T09 — Testar round-trip de pedido e itens após limpar o contexto, dados e cálculos preservados, moeda derivada, reutilização de entradas sem compartilhar identidade, estados terminais, rejeição de recarga inconsistente e invariantes unitárias. Referências: CA02–CA03.
- [x] T10 — Testar constraints com SQL direto e transações isoladas, incluindo preços válidos e inválidos, nulos, status, PK/unique/FK e ausência de exclusão em cascata. Referências: CA04, CA07.
- [x] T11 — Testar datas e incremento de versão, conflito entre dois contextos e rollback sem perda do primeiro resultado confirmado. Referência: CA05.
- [x] T12A — **Após T01B e implementação:** testar round-trip após flush/clear das entidades e da chave composta, igualdade/hash da chave, preservação dos IDs e datas; payload opaco com texto não JSON, Unicode e quebras de linha; `published_at` nulo e com data por SQL/recarga. Datas comparadas em microssegundos. Referência: CA06.
- [x] T12B — **Após T01B e implementação:** testar SQL direto em transações isoladas para cada nulo obrigatório, PK duplicada de Outbox, par de processamento duplicado e mesmo UUID em consumidores distintos. Confirmar rollback sem sobrescrita do registro original e rejeição dos argumentos obrigatórios nulos pela aplicação. Referências: CA06, CA09.
- [x] T12C — **Após T01B e implementação:** testar banco vazio com V1+V2 e upgrade V1→V2 em PostgreSQL descartável com pedido/itens já gravados, confirmando preservação dos dados e versão. Conferir histórico e reaplicação com zero migrations; verificar que V1 não mudou. Referência: CA01B.
- [x] T12D — **Após T01B e implementação:** adaptar `OrdersServiceApplicationTests` para duas migrations e presença das quatro tabelas, removendo a expectativa corrente de ausência de Outbox/processamento. Manter os testes de domínio, persistência e falhas anteriores, sem remover suas verificações de negócio. CA01A permanece evidência histórica de V1. Referências: CA01B, CA02–CA05, CA07–CA08.
- [x] T13 — Executar `mvnw.cmd clean verify`, conferir JAR e relatórios JaCoCo, registrar comandos, contagem real e cobertura; não substituir testes bloqueados por evidência presumida. Referência: CA08; RNF02–RNF05.
- [x] T14 — Documentar esquema, migrations, execução e limites de exclusão, ordem, SQL direto e integrações futuras. Atualizações do roadmap e estado são responsabilidade do coordenador. Referência: CA08.
- [x] T13B — **Após T01B e implementação:** executar o wrapper Maven (`bash ./mvnw clean verify` em Linux ou `mvnw.cmd clean verify` em Windows), conferir JAR com V1/V2 sem recursos exclusivos de teste e registrar contagem real, cobertura JaCoCo e eventuais bloqueios. Não substituir Docker por banco local nem declarar resultados presumidos. Referências: CA08; RNF02–RNF05.
- [x] T14B — **Após T01B e validação:** atualizar documentação de persistência/estado/roadmap para o esquema completo somente com evidências, distinguindo tabelas implementadas de fluxos ainda ausentes. Registrar limites de precisão temporal e integrações externas não executadas. Referência: CA08.

## Revisão e encerramento

- [x] T15 — Delegar revisão independente do recorte autorizado ao `code_reviewer`, corrigir achados e repetir a revisão. Conferir CA01A, CA02–CA05, CA07–CA08 e ausência de ampliação para as tabelas pendentes.
- [x] T16 — Registrar arquivos, testes executados, cobertura, riscos e pendências; concluir o recorte autorizado só com evidências e manter CA01B/CA06 e tarefas de mensageria pendentes. Não executar commit, push ou iniciar a funcionalidade seguinte automaticamente.

- [x] T16B — **Após T01B e validação:** relatar arquivos, comandos efetivamente executados, cobertura, riscos e pendências; concluir o item 03 apenas com CA01B, CA06, CA08 atualizado e CA09 demonstrados e regressões preservadas. Não iniciar o item 04, criar commits ou invocar `code_reviewer` automaticamente; conforme AGENTS.md atual, revisão somente mediante solicitação do usuário. T15 é registro histórico da revisão anterior. Referências: RNF05 e critérios de aceitação.

## Checklist

- [x] Recorte de `OrderItem` JPA com Lombok e suporte indispensável aprovado explicitamente.
- [x] Proposta completa das quatro tabelas aprovada explicitamente.
- [x] Duas tabelas e mapeamentos de pedido/itens validados no PostgreSQL descartável.
- [x] Quatro tabelas e mapeamentos validados no PostgreSQL descartável.
- [x] V1 preservada e upgrade V1→V2 com dados existentes validado.
- [x] Entidades de mensageria seguem Lombok e não expõem setters ou construção sem validação.
- [x] Chave composta, payload opaco, datas, nulabilidade e duplicatas validados.
- [x] Suíte adaptada ao esquema completo, build, JAR e nova cobertura registrados.
- [x] Documentação atualizada com evidências do restante e aprovação do próximo item ainda separada.
- [x] Invariantes e snapshots preservados em memória e após recarga.
- [x] Precisão, constraints e concorrência testadas.
- [x] Build, artefato e cobertura registrados.
- [x] Revisão independente do recorte autorizado concluída e documentação coerente com evidências.

## Evidências do recorte autorizado

- `OrderItem` convertido em classe JPA/Lombok, com UUID próprio, vínculo ao pedido e construtor protegido. `Order` fornece o suporte JPA do agregado, moeda única, timestamps e versão otimista. Sem setters públicos.
- Migration `V1__create_orders_and_order_items.sql` cria somente `orders` e `order_items`. Testes validam schema, round-trip, snapshots, estados, timestamps, conflito otimista, campos obrigatórios, PKs, FK, unicidade e precisão numérica sem arredondamento.
- Regressão reproduziu falha na carga direta de `OrderItem` antes do pai lazy; callbacks e comparação de vínculo foram corrigidos e os testes passaram. Dados inconsistentes na reidratação geram falha identificável.
- `mvnw.cmd clean verify`: 19 testes, zero falhas, erros ou skips (8 domínio, 8 persistência, 1 contexto/migration e 2 falhas de infraestrutura/persistência esperadas).
- JaCoCo por linhas: `OrderItem` 55/61 (90,2%), `Order` 65/69 (94,2%), `OrderStatus` 4/4 (100%); branches 32/40 em cada entidade (80%). Relatórios em `target/site/jacoco/`.
- JAR inclui migration real e exclui migrations de falha de teste. Validação com PostgreSQL Testcontainers, sem acesso a `orders_db` nesta etapa.
- Limites: ordem dos itens não persistida, sem remoção em cascata; moeda de item carregado diretamente deve ser resolvida com contexto aberto antes de uso detached. Detalhes em [persistence.md](../../docs/persistence.md).
- CA01A, CA02–CA05 e CA07–CA08 validados no recorte de pedido/itens; CA01B e CA06 continuam pendentes de aprovação e implementação.

## Preparação do restante — 28/10/2026

- [x] P01 — Conferir roadmap, estado, specs, diagrama, entidades com Lombok, V1 e testes existentes; identificar que concluir Outbox/processamento é o próximo recorte do item 03. Referências: contexto e decisões 17–21.
- [x] P02 — Atualizar somente `spec.md` e `tasks.md` desta funcionalidade, corrigindo inventário obsoleto e detalhando decisões, critérios e tarefas da proposta restante. Preservar evidências e checkboxes do recorte implementado; T07/T12 pendentes foram detalhados em subtarefas.
- [x] P03 — Receber aprovação explícita do usuário para a proposta restante antes de delegar implementação. Referências: T01B e RNF05.

Validação desta preparação: leitura e conferência documental contra o código existente. Nenhum teste, build, SQL ou migration foi executado; não há nova medição de cobertura. As contagens e percentuais da seção anterior são registros históricos, sem reexecução nesta tarefa. Nenhuma implementação de mensageria foi iniciada.

## Conclusão do restante — 28/10/2026

- Aprovação explícita recebida após apresentação da spec: “implemente”. Implementados somente o restante do item 03 e seus testes; item 04 não iniciado.
- V2 cria `outbox_messages` e `processed_messages`. V1 foi comparada byte a byte ao arquivo em `HEAD` e permanece inalterada. Entidades `OutboxMessage`, `ProcessedMessage` e chave `ProcessedMessageId` usam Lombok, acesso JPA por campo e construção validada, sem setters públicos.
- `bash ./mvnw clean verify`: **28 testes, zero falhas, erros ou skips**, com Maven 3.10.0, Amazon Corretto 25.0.4.1 e PostgreSQL Testcontainers `postgres:16.9-alpine`. Distribuição: 8 domínio de pedido, 4 unitários de mensagens, 8 persistência de pedido, 4 persistência de mensagens, 1 upgrade de migration, 1 contexto/migrations e 2 falhas esperadas.
- CA01B: bootstrap em banco vazio com V1/V2 e JPA `validate`; upgrade V1→V2 em schema isolado com pedido/itens existentes preserva todas as colunas, snapshots, datas e versão; histórico e reexecução sem reaplicar migrations verificados.
- CA06/CA09: round-trip após flush/clear, UUIDs, chave composta/igualdade/hash, consumidor distinto para mesmo UUID, payload não JSON com Unicode/quebras de linha, datas de inserção e publicação nula/com data, nulos obrigatórios, duplicatas e rollback sem sobrescrita validados. O teste de duplicata comprova também rollback de outra inserção na mesma transação.
- A primeira execução revelou dois problemas no novo teste de upgrade (contagem do registro técnico de criação de schema e restauração do schema da conexão pooled); ambos foram corrigidos no teste. A execução completa posterior passou, incluindo as regressões de pedido/itens.
- JaCoCo da suíte completa: `OutboxMessage` **11/11 linhas (100%), 6/6 branches (100%)**; `ProcessedMessage` **6/6 linhas (100%), sem branches**; `ProcessedMessageId` **6/6 linhas (100%), 4/4 branches (100%)**. As datas de callback são cobertas pelos testes JPA; esses números agregam testes unitários e de integração. Cobertura anterior preservada: `Order` 65/69 linhas, `OrderItem` 55/61, branches 32/40 em cada; `OrderStatus` 4/4 linhas.
- CA08: JAR inspecionado via ZIP; contém somente V1/V2 na árvore `db/`, sem recursos exclusivos de falha. Relatórios em `target/site/jacoco/` e `target/surefire-reports/`. Documentação de persistência, estado e roadmap atualizada.
- Limites: banco local não acessado e nenhuma migration nele executada; sem Kafka, catálogo, API, publicador, consumidor ou deduplicação operacional. `published_at` com data foi validado via SQL, sem API de publicação. Datas persistidas comparadas com tolerância de um microssegundo e estáveis após nova recarga.
- Não houve revisão independente nesta conclusão: AGENTS.md atual exige solicitação do usuário. O registro T15 é histórico do recorte anterior. Sem commits, push ou Merge Request.
