# Modelo relacional de pedidos

Estado: **recorte de pedido/itens implementado e validado; proposta de mensageria pendente**. Este documento especifica a evolução de persistência proposta em [database.md](../../docs/diagrams/database.md). A proposta completa das quatro tabelas não foi aprovada; evidências do recorte autorizado em [tasks.md](tasks.md).

## Aprovação parcial e limite de execução

O usuário solicitou explicitamente: “transforme o orderItem em uma entidade jpa agora, e use lombok para as anotacoes”. Está autorizado o recorte de `OrderItem` como entidade JPA com Lombok e seu suporte indispensável: mapeamento de `Order` e do vínculo, migrations apenas de `orders` e `order_items`, preservação de validações e snapshots, moeda/reidratação e testes correspondentes. As decisões desta spec aplicáveis a essas duas tabelas delimitam esse suporte.

`outbox_messages` e `processed_messages`, seus mapeamentos e seus testes continuam **propostas pendentes de aprovação**. Não criar essas tabelas ou classes nesta implementação parcial. Publicação, consumo e contratos externos também não foram autorizados. A conclusão do recorte de pedido/itens não conclui a proposta das quatro tabelas.

## Objetivo

Preparar o esquema PostgreSQL e os mapeamentos JPA de `orders`, `order_items`, `outbox_messages` e `processed_messages` conforme o diagrama, preservando as regras aprovadas do domínio. As duas tabelas de mensageria serão apenas estruturas de armazenamento; publicação, consumo, contratos e deduplicação operacional pertencem às funcionalidades posteriores.

## Contexto

- A [base de persistência](../base-persistencia-integracao/spec.md) e o [domínio](../dominio-pedidos/spec.md) foram implementados e validados nas etapas anteriores. Há Spring Data JPA, PostgreSQL, Flyway, Lombok e testes com PostgreSQL Testcontainers.
- `Order` e `OrderItem` estão em `domain.entity`, mas não têm anotações JPA. `OrderItem` é um `record`; `OrderStatus` está em `domain.enums`. Não existem migrations de produção ou repositórios de negócio.
- O diagrama é uma proposta. O suporte de pedido/itens foi autorizado no recorte acima; as decisões relativas às tabelas de mensageria continuam pendentes de aprovação antes de implementação.
- A ausência de migrations de produção e de tabelas de negócio foi um limite da entrega inicial, não uma proibição permanente. Esta funcionalidade propõe introduzi-las sem alterar as evidências históricas daquela entrega.

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

## Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF01 | Criar migrations Flyway versionadas para nomes, tipos e constraints das decisões 1–5. Nesta aprovação parcial, criar somente `orders` e `order_items`; as duas tabelas de mensageria aguardam aprovação separada. Hibernate continua em `ddl-auto=validate`. |
| RF02 | Mapear entidades JPA conforme decisões 6–12, preservando factories, validações, snapshots e cálculos aprovados. Nesta aprovação parcial, mapear apenas `Order` e `OrderItem`; mapeamentos de mensageria e chave composta estão pendentes. |
| RF03 | Persistir e recarregar pedido e itens em transação, mantendo identificadores, moeda, estado e valores, sem duplicação de agregados ou coluna extra. |
| RF04 | Registrar timestamps e concorrência otimista conforme decisões 13–15; conflito não pode sobrescrever silenciosamente a alteração já confirmada. |
| RF05 | **Pendente de aprovação:** permitir round-trip de registros de Outbox e processamento com os campos do diagrama, sem implementar fluxos de integração, conforme decisão 16. |
| RF06 | Adaptar testes anteriores à presença de migrations reais de produção e documentar o novo esquema, comandos e resultados. Não depender de migrations fictícias de produção para provar Flyway. |

## Requisitos não funcionais

- **RNF01 — Preservação:** não executar `Flyway clean`, reparo, baseline automático, recriação de banco ou deleção de dados. Migration nova deve falhar de forma visível se encontrar esquema incompatível.
- **RNF02 — Isolamento:** migrations e cenários de erro são testados em PostgreSQL Testcontainers descartável, sem modificar `orders_db` nos testes.
- **RNF03 — Coerência:** não duplicar modelos por antecipação, adicionar DTOs, repositórios genéricos ou serviços sem um caso desta especificação. Os testes podem usar `EntityManager` diretamente.
- **RNF04 — Independência externa:** testes desta entrega não exigem Produtos, Estoque ou Kafka.
- **RNF05 — Evidência e autorização:** registrar comandos, testes, cobertura e limitações reais. A aprovação parcial permite apenas o recorte de pedido/itens descrito no início; a criação deste documento não autoriza implementar os demais componentes.

## Critérios de aceitação

| ID | Cenário e evidência | Requisitos |
| --- | --- | --- |
| CA01A | **Recorte autorizado:** em PostgreSQL descartável vazio, Flyway cria somente `orders` e `order_items` além de seu histórico; JPA inicia com validação do esquema. Reexecutar a migration não repete sua aplicação. Não existem tabelas de Outbox ou processamento. | RF01; RNF01–RNF02 |
| CA01B | **Etapa futura pendente de aprovação:** após autorização das tabelas de mensageria, o esquema completo contém as quatro tabelas propostas além do histórico. Não marcar este critério como concluído com a entrega de duas tabelas. | RF01; RNF05 |
| CA02 | Salvar, limpar o contexto de persistência e recarregar pedido com ao menos dois itens. IDs, produto, nome, preço, quantidade, moeda, estado e cálculos são preservados; moeda existe somente na tabela do pedido e totais não têm colunas. Reutilizar uma entrada de item em outro pedido não reaproveita sua identidade persistida. | RF02–RF03 |
| CA03 | As regras unitárias aprovadas continuam passando após a conversão do record e o mapeamento; snapshot não pode ser modificado pelo chamador. Recarga de `AWAITING_PAYMENT` ou `REJECTED` preserva o estado e rejeita as operações incompatíveis. Um agregado inconsistente introduzido por SQL direto, como pedido vazio ou moeda desconhecida, não é retornado como pedido válido. | RF02–RF03 |
| CA04 | Escritas SQL diretas isoladas comprovam rejeição de nulos obrigatórios, quantidade não positiva, preço negativo/não finito/`1.001`, FK inválida, produto repetido e status fora da enumeração. Preços `0`, `1.000` e valor grande do teste de domínio existente são aceitos sem arredondamento. | RF01–RF02 |
| CA05 | INSERT de pedido preenche as duas datas; atualização de estado preserva criação, atualiza alteração e incrementa versão. Dois contextos concorrentes carregam a mesma versão: o primeiro confirma alteração e o segundo recebe conflito otimista; seu rollback preserva o primeiro resultado. | RF04 |
| CA06 | **Pendente de aprovação:** salvar e recarregar Outbox com payload textual, chave e tópico; `published_at` aceita nulo e data. Na tabela de processamento, repetir `(consumer, message_id)` falha e usar outro consumidor com o mesmo ID funciona. Não são criados publicadores ou consumidores. | RF05 |
| CA07 | A FK impede exclusão de pedido com itens por SQL direto; o mapeamento não tem remoção em cascata. Nenhum cenário introduz FK externa, coluna de posição ou tabela de envelope. Testes não presumem a ordem de itens após recarga. | RF01–RF04 |
| CA08 | `mvnw.cmd clean verify` passa com testes de domínio e persistência do recorte autorizado, relatórios JaCoCo e recursos de migration de teste fora do JAR de produção. Documentação registra o esquema parcial e suas pendências. Validar o JAR e as migrations reais, adaptando a suíte anterior sem usar banco local como substituto de containers. | RF06; RNF02–RNF05 |

## Casos de erro

| Caso | Resultado proposto |
| --- | --- |
| Preço que exige arredondamento ou valor numérico não finito | Rejeição na validação da aplicação quando representável em Java e nas constraints SQL; nenhuma persistência parcial. |
| Item órfão, produto duplicado, PK duplicada ou campo obrigatório ausente | Banco rejeita e a transação é revertida. |
| Pedido sem itens | Factory/gravação do agregado rejeita; não prometer que a FK sozinha impede INSERT SQL direto de pedido vazio. |
| Atualização concorrente obsoleta | Exceção de concorrência otimista e rollback, sem retry automático. |
| Falha JPA ou dados inconsistentes durante a reidratação | Propagar falha identificável, sem gerar novos IDs, redefinir estado, devolver agregado parcial ou reparar dados automaticamente. |
| Remoção de pedido que possui itens | Rejeição pela FK; sem cascata ou limpeza automática. |
| Esquema existente incompatível, migration inválida ou infraestrutura ausente | Falha visível; registrar bloqueio sem aplicar reparos destrutivos ou declarar sucesso. |

## Fora de escopo

Endpoints e DTOs, catálogo, criação pública de pedidos, contrato de envelope/payload, envio Kafka, polling e índices de publicador, consumo de eventos, confirmação de offsets, política operacional de deduplicação, retries e retenção, cancelamento, pagamentos, notificações, exclusão de agregados e migração de dados legados. Atomicidade de criação com comando de estoque e transação de consumo pertencem às suas funcionalidades, que usarão este esquema.

## Decisões e aprovação remanescente

O recorte de pedido/itens autoriza entidades JPA diretas com conversão de `OrderItem` para classe; IDs de item próprios e cópias por agregado; timestamps pela aplicação; `@Version`; ausência de ordem persistida e de remoção em cascata; preço `numeric` sem teto artificial e com check de escala e finitude; moeda somente no pedido e valores derivados, como suporte da entidade solicitado. Permanecem para aprovação o acréscimo de `outbox_messages` e `processed_messages` e seus mapeamentos. Se qualquer decisão for alterada, revisar esta spec antes de implementar. Não há contrato externo pressuposto.
