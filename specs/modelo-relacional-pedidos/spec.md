# Modelo relacional de pedidos

Estado: **item 03 implementado e validado em 28/10/2026**, após aprovação explícita do restante. Este documento especifica a evolução de persistência proposta em [database.md](../../docs/diagrams/database.md). O usuário aprovou o restante com a instrução “implemente”, após receber os documentos; evidências de execução em [tasks.md](tasks.md).

## Aprovação parcial e limite de execução

O usuário solicitou explicitamente: “transforme o orderItem em uma entidade jpa agora, e use lombok para as anotacoes”. Está autorizado o recorte de `OrderItem` como entidade JPA com Lombok e seu suporte indispensável: mapeamento de `Order` e do vínculo, migrations apenas de `orders` e `order_items`, preservação de validações e snapshots, moeda/reidratação e testes correspondentes. As decisões desta spec aplicáveis a essas duas tabelas delimitam esse suporte.

Em 28/10/2026, após apresentação desta especificação e das tarefas, o usuário autorizou explicitamente: “implemente”. Estão aprovados `outbox_messages` e `processed_messages`, seus mapeamentos e testes nas decisões 17–21. A aprovação cobre somente a conclusão do item 03; publicação, consumo, contratos externos e criação pública de pedidos permanecem nas funcionalidades posteriores.

## Objetivo

Preparar o esquema PostgreSQL e os mapeamentos JPA de `orders`, `order_items`, `outbox_messages` e `processed_messages` conforme o diagrama, preservando as regras aprovadas do domínio. As duas tabelas de mensageria serão apenas estruturas de armazenamento; publicação, consumo, contratos e deduplicação operacional pertencem às funcionalidades posteriores.

## Contexto

- A [base de persistência](../base-persistencia-integracao/spec.md) e o [domínio](../dominio-pedidos/spec.md) foram implementados e validados nas etapas anteriores. Há Spring Data JPA, PostgreSQL, Flyway, Lombok e testes com PostgreSQL Testcontainers.
- `Order` e `OrderItem` já são classes JPA em `domain.entity`, com Lombok (`@Getter`, `@Accessors(fluent = true)` e `@NoArgsConstructor(access = AccessLevel.PROTECTED)`); `OrderStatus` está em `domain.enums`. A migration de produção `V1__create_orders_and_order_items.sql` cria as duas tabelas. No início deste recorte não existiam entidades ou migrations de Outbox/processamento nem repositórios de negócio. A conclusão acrescentou `OutboxMessage`, `ProcessedMessage`, `ProcessedMessageId` e V2; não foram necessários repositórios de negócio.
- Este recorte conclui o item 03 do [roadmap](../../docs/implementation-roadmap.md), antes do item 04 de criação de pedidos. O inventário inicial do diagrama e da arquitetura antecede o recorte JPA implementado; código, evidências registradas e esta spec delimitam o estado atual.
- O diagrama é uma proposta. O suporte de pedido/itens foi autorizado no recorte acima; as decisões relativas às tabelas de mensageria foram aprovadas na autorização de 28/10/2026.
- A ausência de migrations de produção e de tabelas de negócio foi um limite histórico do item 01. V1 já introduziu pedido/itens; a conclusão proposta acrescenta somente as duas tabelas restantes por migration incremental, sem modificar V1 ou as evidências históricas.

## Regras de negócio preservadas

Um pedido contém pelo menos um item, produtos não se repetem e todos os itens usam a moeda do pedido. Nome e preço são snapshots históricos; quantidade é inteira positiva e preço é decimal finito não negativo, representável exatamente em duas casas. Não há arredondamento, conversão monetária ou limite monetário adicional de negócio.

O pedido nasce em `AWAITING_STOCK`. As únicas operações deste recorte continuam sendo reserva aceita para `AWAITING_PAYMENT` e reserva recusada para `REJECTED`; operações incompatíveis falham sem mutação. A persistência não deve contornar as validações por setters públicos.

## Decisões de persistência propostas

### Esquema e integridade

Os nomes e tipos seguem o diagrama. Todos os campos são obrigatórios, exceto `outbox_messages.published_at`.

| Tabela | Colunas e proteções propostas |
| --- | --- |
| `orders` | `id uuid` PK; `status text` restrito a `AWAITING_STOCK`, `AWAITING_PAYMENT`, `REJECTED`; `currency text`; `created_at timestamptz`; `updated_at timestamptz`; `version bigint`. |
| `order_items` | `id uuid` PK; `order_id uuid` FK para `orders.id`; `product_id uuid`; `product_name text`; `unit_price numeric` sem precisão/escala coercitiva; `quantity integer`; `UNIQUE (order_id, product_id)`; checks de quantidade positiva e preço finito, não negativo e exatamente representável em duas casas. |
| `outbox_messages` | `id uuid` PK; `topic text`; `partition_key text`; `payload text`; `created_at timestamptz`; `published_at timestamptz` anulável. Sem FK ou restrição baseada no conteúdo do payload. |
| `processed_messages` | `consumer text` e `message_id uuid` como PK composta; `processed_at timestamptz`. Sem FK para pedido. |

1. A moeda é persistida apenas em `orders.currency`. A validade ISO 4217 continua sendo validada pela aplicação; não introduzir uma tabela local de moedas nem uma lista SQL fixa de códigos.
2. A constraint de preço deve avaliar o valor recebido antes de qualquer arredondamento. Proposta: `numeric` sem typmod e check equivalente a valor finito, `unit_price >= 0` e `unit_price = trunc(unit_price, 2)`. Excluir explicitamente valores PostgreSQL `NaN` e infinitos. Uma coluna `numeric(p,2)` isolada não atende ao requisito, pois pode arredondar antes do check.
3. `1`, `1.0` e `1.000` continuam aceitos; `1.001` é rejeitado também em escrita SQL direta. Não fixar `p` nem um teto monetário novo; valem os limites técnicos inerentes ao PostgreSQL e ao `BigDecimal`.
4. A FK impede item órfão. A existência mínima de um item é validada na construção e na gravação do agregado em transação; não criar trigger que exija item durante o INSERT inicial de `orders`. SQL direto de terceiros não recebe garantia adicional dessa cardinalidade mínima.
5. Não incluir `currency`, total, subtotal, índice de posição ou outras colunas em `order_items`. Não criar tabelas de catálogo, estoque ou envelope.

### Entidades e reidratação

6. Proposta: mapear diretamente as classes de domínio para evitar um segundo modelo equivalente. `OrderItem` passa a ser classe convencional compatível com JPA; `Order`, `OrderItem`, `OutboxMessage` e `ProcessedMessage` ficam em `domain.entity`, o enum continua em `domain.enums`. A chave composta pode ter classe de suporte própria. Não criar DTOs nesta etapa.
7. Usar Lombok para acessores e código repetitivo quando útil; construtores exigidos por JPA ficam protegidos. Manter a factory `Order.create`, o construtor público de snapshot de `OrderItem` e acessores fluentes existentes. Não gerar setters, builders ou construtores públicos que ignorem as validações.
8. Proposta: o UUID de pedido continua fornecido à factory; UUIDs próprios dos itens e das novas mensagens são gerados na aplicação. Ao formar um novo agregado, copiar snapshots dos itens e atribuir identidades próprias aos itens desse pedido; reutilizar a mesma entrada em dois pedidos não deve compartilhar uma entidade persistida. O identificador recebido da mensagem processada é informado pelo chamador, sem gerar outro `message_id`.
9. Preservar a semântica de valor existente dos snapshots de item para seus campos de negócio, sem incluir ID de persistência ou referência ao pedido em igualdade, hash ou texto recursivo. A coleção exposta permanece imutável para o chamador. Não permitir alteração de produto, nome, preço ou quantidade após criação.
10. O vínculo com o pedido fornece a moeda aos itens carregados. Uma instância recém-construída de snapshot ainda pode receber moeda para a validação anterior à associação. Após recarga, `currency()`, `subtotal()` e `total()` devem produzir os mesmos valores de negócio, sem persistir moeda redundante ou total em cache no banco.
11. Total e subtotais são calculados dos itens persistidos, com escala 2. A recarga não pode usar a factory de criação de modo que gere novos IDs, altere timestamps ou redefina o status para `AWAITING_STOCK`. A leitura de um agregado inconsistente com as invariantes aprovadas deve falhar visivelmente, sem retornar pedido válido parcial, converter dados ou tentar repará-los.
12. **Limitação explícita do recorte de persistência: não há promessa de ordem de itens após recarga**, pois o diagrama não contém posição. A ordem de entrada em um pedido ainda não persistido pode ser mantida; testes de round-trip comparam itens por identidade ou produto. Uma futura exigência de ordem persistida precisará de nova decisão e migration.

### Timestamps, concorrência e ciclo de vida

13. Proposta: usar `Instant` e atribuição pela aplicação nos callbacks de persistência. No INSERT de pedido, `created_at` e `updated_at` recebem o mesmo instante; `created_at` é preservado em alterações posteriores e `updated_at` é atualizado quando o pedido muda. Datas de Outbox e processamento são atribuídas ao registrar essas linhas. Não exigir igualdade de nanossegundos após recarga: PostgreSQL armazena precisão de microssegundos.
14. Mapear `orders.version` com `@Version`. A versão inicial é gerenciada pelo provedor JPA e aumenta em atualização do pedido; uma atualização com versão obsoleta deve falhar e ser revertida. Não implementar retry nem resolver conflitos automaticamente. Apenas a linha do pedido é versionada neste esquema.
15. Proposta: persistir itens junto ao agregado com cascade de persistência. Não expor remoção de itens nem configurar `orphanRemoval`, cascade de remoção ou `ON DELETE CASCADE` nesta etapa. A FK deve impedir excluir um pedido que ainda possua itens; não criar API de exclusão. Atualizações de status não reescrevem snapshots.
16. Em Outbox, `published_at` nulo representa pendência; o payload é texto opaco, sem validação de JSON, envelope ou tópico de negócio. Na tabela de processamento, a PK impede repetir o mesmo `(consumer, message_id)` e permite o mesmo ID para consumidores diferentes. Esses fatos de armazenamento não representam um publicador ou consumidor implementado.

## Recorte restante aprovado

As decisões abaixo detalham somente `outbox_messages` e `processed_messages`, sem reabrir o comportamento de pedido/itens já entregue.

17. Acrescentar `V2__create_outbox_and_processed_messages.sql` em `src/main/resources/db/migration/`, preservando integralmente V1. A migration cria as duas tabelas com os tipos, PKs e nulabilidade definidos acima. Não acrescentar FKs, índices de polling, defaults de geração pelo banco, novas colunas ou constraints de conteúdo. Além dos índices das PKs, os índices operacionais ficam para as etapas que definirem as consultas.
18. Mapear `OutboxMessage` e `ProcessedMessage` em `domain.entity`, com acesso JPA por campo. Reutilizar o padrão Lombok existente de getters fluentes e construtor sem argumentos protegido; não utilizar `@Data`, setters públicos, builders ou construtores gerados que dispensem validação. Métodos explícitos de criação validam os campos obrigatórios recebidos antes de permitir persistência. Não adicionar regra de tópico, tamanho, JSON, envelope, consumidor permitido ou rejeição de texto vazio nesta etapa: a exigência física aqui é não nulidade.
19. A criação de `OutboxMessage` recebe `topic`, `partitionKey` e `payload`, gera seu UUID na aplicação e inicia `publishedAt` nulo. O callback de inserção atribui `createdAt` uma vez. Identidade, tópico, chave, payload e criação são preservados após recarga; apenas `published_at` é uma coluna atualizável. Sua aceitação de uma data pode ser comprovada por atualização SQL seguida de recarga, sem introduzir operação de publicação ou confirmação Kafka nesta etapa.
20. Usar uma chave `ProcessedMessageId` com `@Embeddable`/`@EmbeddedId`, composta por `consumer` e `messageId`, com igualdade/hash baseados somente nesses dois valores, sem mutação pública. Usar Lombok também nesse código repetitivo e construtor sem argumentos protegido compatível com JPA. A criação de `ProcessedMessage` recebe consumidor e UUID da mensagem original, preservando-os; o callback de inserção atribui `processedAt` uma vez. Nenhum campo desse registro é atualizável. Não há geração de outro UUID nem tratamento de duplicata como sucesso nesta etapa.
21. `Instant` continua sendo o tipo Java de datas, com precisão comparada após normalização para microssegundos nos testes. Não redefinir IDs ou datas ao recarregar. As novas entidades não têm associação com `Order`, cascade ou `@Version`. Persistência isolada via `EntityManager` é suficiente; repositórios, serviços, jobs e handlers não são necessários neste recorte.

## Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF01 | Criar migrations Flyway versionadas para nomes, tipos e constraints das decisões 1–5. A aprovação parcial anterior criou somente `orders` e `order_items`; as duas tabelas de mensageria foram aprovadas separadamente em 28/10/2026. Acrescentar V2 conforme decisão 17, sem editar V1. Hibernate continua em `ddl-auto=validate`. |
| RF02 | Mapear entidades JPA conforme decisões 6–12, preservando factories, validações, snapshots e cálculos aprovados. A aprovação parcial anterior mapeou `Order` e `OrderItem`; mapeamentos de mensageria e chave composta foram aprovados em 28/10/2026. |
| RF03 | Persistir e recarregar pedido e itens em transação, mantendo identificadores, moeda, estado e valores, sem duplicação de agregados ou coluna extra. |
| RF04 | Registrar timestamps e concorrência otimista conforme decisões 13–15; conflito não pode sobrescrever silenciosamente a alteração já confirmada. |
| RF05 | **Aprovado em 28/10/2026:** permitir round-trip de registros de Outbox e processamento com os campos do diagrama, sem implementar fluxos de integração, conforme decisões 16–21. |
| RF06 | Adaptar testes anteriores à presença das quatro tabelas e duas migrations reais de produção, incluindo `OrdersServiceApplicationTests`, que atualmente exige ausência das tabelas de mensageria e apenas uma migration. Documentar o novo esquema, comandos e resultados sem alterar as evidências históricas. Não depender de migrations fictícias de produção para provar Flyway. |

## Requisitos não funcionais

- **RNF01 — Preservação:** não executar `Flyway clean`, reparo, baseline automático, recriação de banco ou deleção de dados. Migration nova deve falhar de forma visível se encontrar esquema incompatível.
- **RNF02 — Isolamento:** migrations e cenários de erro são testados em PostgreSQL Testcontainers descartável, sem modificar `orders_db` nos testes.
- **RNF03 — Coerência:** não duplicar modelos por antecipação, adicionar DTOs, repositórios genéricos ou serviços sem um caso desta especificação. Os testes podem usar `EntityManager` diretamente.
- **RNF04 — Independência externa:** testes desta entrega não exigem Produtos, Estoque ou Kafka.
- **RNF05 — Evidência e autorização:** registrar comandos, testes, cobertura e limitações reais. A aprovação parcial permite apenas o recorte de pedido/itens descrito no início; a criação deste documento não autoriza implementar os demais componentes. A aprovação do recorte restante autoriza somente as decisões 17–21 e critérios correspondentes deste item, sem autorizar o item 04.

## Critérios de aceitação

| ID | Cenário e evidência | Requisitos |
| --- | --- | --- |
| CA01A | **Evidência histórica do recorte V1 autorizado:** em PostgreSQL descartável vazio, Flyway cria somente `orders` e `order_items` além de seu histórico; JPA inicia com validação do esquema. Reexecutar a migration não repete sua aplicação. Nesse recorte não existem tabelas de Outbox ou processamento. Após aprovação de V2, a inicialização corrente passa a ser verificada por CA01B; este registro não exige remover as novas tabelas. | RF01; RNF01–RNF02 |
| CA01B | **Aprovado em 28/10/2026:** em PostgreSQL descartável vazio, V1 e V2 criam as quatro tabelas propostas além do histórico e JPA inicia com `ddl-auto=validate`. Em outro cenário descartável, aplicar somente V1, inserir pedido/itens, depois aplicar V2: dados, snapshots, IDs, estado, datas e versão existentes são preservados. O histórico contém ambas as migrations e nova execução aplica zero migrations. V1 permanece inalterada; não marcar concluído com a entrega de duas tabelas. | RF01; RNF05 |
| CA02 | Salvar, limpar o contexto de persistência e recarregar pedido com ao menos dois itens. IDs, produto, nome, preço, quantidade, moeda, estado e cálculos são preservados; moeda existe somente na tabela do pedido e totais não têm colunas. Reutilizar uma entrada de item em outro pedido não reaproveita sua identidade persistida. | RF02–RF03 |
| CA03 | As regras unitárias aprovadas continuam passando após a conversão do record e o mapeamento; snapshot não pode ser modificado pelo chamador. Recarga de `AWAITING_PAYMENT` ou `REJECTED` preserva o estado e rejeita as operações incompatíveis. Um agregado inconsistente introduzido por SQL direto, como pedido vazio ou moeda desconhecida, não é retornado como pedido válido. | RF02–RF03 |
| CA04 | Escritas SQL diretas isoladas comprovam rejeição de nulos obrigatórios, quantidade não positiva, preço negativo/não finito/`1.001`, FK inválida, produto repetido e status fora da enumeração. Preços `0`, `1.000` e valor grande do teste de domínio existente são aceitos sem arredondamento. | RF01–RF02 |
| CA05 | INSERT de pedido preenche as duas datas; atualização de estado preserva criação, atualiza alteração e incrementa versão. Dois contextos concorrentes carregam a mesma versão: o primeiro confirma alteração e o segundo recebe conflito otimista; seu rollback preserva o primeiro resultado. | RF04 |
| CA06 | **Aprovado em 28/10/2026:** persistir, executar flush, limpar o contexto e recarregar Outbox: UUID, tópico, chave, payload textual não JSON (inclusive quebras de linha e Unicode) e criação são preservados; `published_at` inicia nulo, aceita data via SQL e é recarregado. Persistir e recarregar processamento preserva consumidor, UUID recebido e data; repetir `(consumer, message_id)` falha e outro consumidor com o mesmo UUID funciona. Identidade composta tem igualdade/hash coerentes. Datas não mudam na recarga; comparar em microssegundos. Não são criados publicadores ou consumidores. | RF05 |
| CA07 | A FK impede exclusão de pedido com itens por SQL direto; o mapeamento não tem remoção em cascata. Nenhum cenário introduz FK externa, coluna de posição ou tabela de envelope. Testes não presumem a ordem de itens após recarga. | RF01–RF04 |
| CA08 | O wrapper Maven (`bash ./mvnw clean verify` em Linux ou `mvnw.cmd clean verify` em Windows) passa com testes de domínio e persistência do recorte autorizado, relatórios JaCoCo e recursos de migration de teste fora do JAR de produção. Com a implementação do restante, a documentação registra o esquema completo e os limites de mensageria. Validar o JAR e as migrations reais, adaptando a suíte anterior sem usar banco local como substituto de containers. | RF06; RNF02–RNF05 |
| CA09 | SQL direto em transações isoladas rejeita nulo em cada coluna obrigatória das novas tabelas, PK duplicada de Outbox e PK composta duplicada de processamento; `published_at` nulo é aceito. Duplicata não sobrescreve o registro original e a transação falha com rollback. Criação pela aplicação rejeita argumentos obrigatórios nulos sem permitir inserção parcial; payload não é interpretado como JSON. | RF01, RF05; RNF02–RNF04 |

## Casos de erro

| Caso | Resultado proposto |
| --- | --- |
| Preço que exige arredondamento ou valor numérico não finito | Rejeição na validação da aplicação quando representável em Java e nas constraints SQL; nenhuma persistência parcial. |
| Item órfão, produto duplicado, PK duplicada ou campo obrigatório ausente | Banco rejeita e a transação é revertida. |
| Pedido sem itens | Factory/gravação do agregado rejeita; não prometer que a FK sozinha impede INSERT SQL direto de pedido vazio. |
| Atualização concorrente obsoleta | Exceção de concorrência otimista e rollback, sem retry automático. |
| Falha JPA ou dados inconsistentes durante a reidratação | Propagar falha identificável, sem gerar novos IDs, redefinir estado, devolver agregado parcial ou reparar dados automaticamente. |
| Remoção de pedido que possui itens | Rejeição pela FK; sem cascata ou limpeza automática. |
| Argumento obrigatório nulo na criação de Outbox ou processamento | Aplicação rejeita antes da inserção; banco também rejeita nulos obrigatórios em SQL direto. |
| PK de Outbox ou `(consumer, message_id)` repetida | Banco rejeita e a transação sofre rollback; não sobrescrever registro, ignorar conflito ou tratar como deduplicação operacional bem-sucedida. |
| Esquema existente incompatível, migration inválida ou infraestrutura ausente | Falha visível; registrar bloqueio sem aplicar reparos destrutivos ou declarar sucesso. |

## Fora de escopo

Endpoints e DTOs, catálogo, criação pública de pedidos, contrato de envelope/payload, envio Kafka, polling e índices de publicador, consumo de eventos, confirmação de offsets, política operacional de deduplicação, retries e retenção, cancelamento, pagamentos, notificações, exclusão de agregados e migração de dados legados. Atomicidade de criação com comando de estoque e transação de consumo pertencem às suas funcionalidades, que usarão este esquema.

## Decisões e aprovação do restante

O recorte de pedido/itens autoriza entidades JPA diretas com conversão de `OrderItem` para classe; IDs de item próprios e cópias por agregado; timestamps pela aplicação; `@Version`; ausência de ordem persistida e de remoção em cascata; preço `numeric` sem teto artificial e com check de escala e finitude; moeda somente no pedido e valores derivados, como suporte da entidade solicitado. Foram aprovados em 28/10/2026 o acréscimo de `outbox_messages` e `processed_messages` por V2; entidades com o padrão Lombok atual; chave composta embutida; UUID da Outbox gerado pela aplicação; UUID de processamento recebido do chamador; timestamps de inserção pela aplicação; e testes de upgrade, round-trip e integridade. A aprovação deste restante conclui a definição do item 03, sem autorizar fluxos de mensageria ou criação de pedidos. Se qualquer decisão for alterada, revisar esta spec antes de implementar. Não há contrato externo pressuposto.

## Dúvidas e validação desta preparação

Não foi identificado contrato externo necessário para criar estas estruturas opacas. As decisões 17–21 foram aprovadas em 28/10/2026; se o usuário desejar outra representação da chave ou política de criação, ajustar a spec antes de implementar. Contratos HTTP/Kafka, interpretação do payload e identificação nominal dos consumidores continuam decisões das funcionalidades posteriores.

Validação documental em 28/10/2026: leitura do roadmap, estado do projeto, diagrama, specs existentes, entidades, V1 e testes de persistência/inicialização. Identificados e corrigidos nesta spec os trechos de inventário anteriores ao JPA já implementado. Não foram executados build, testes, SQL ou migrations nesta preparação; as evidências registradas em `tasks.md` pertencem ao recorte anterior.

## Evidências da conclusão — 28/10/2026

`bash ./mvnw clean verify` concluído com 28 testes, zero falhas, erros ou skips, usando PostgreSQL Testcontainers. A suíte cobre atualização V1→V2 com dados existentes, round-trip, constraints e rollback de duplicatas, além das regressões anteriores. V1 permaneceu inalterada; o JAR contém V1/V2 de produção e exclui migrations exclusivas de teste. JaCoCo das novas classes: `OutboxMessage` 11/11 linhas e 6/6 branches; `ProcessedMessage` 6/6 linhas, sem branches; `ProcessedMessageId` 6/6 linhas e 4/4 branches. Detalhamento e checklist em [tasks.md](tasks.md).

Não houve validação com serviços externos, Kafka ou banco local. Publicação, consumo e deduplicação operacional permanecem fora do escopo; o item 04 exige seu próprio ciclo SDD.
