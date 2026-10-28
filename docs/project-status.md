# Estado do projeto — Pedidos

Atualizado em 28/10/2026, a partir dos arquivos do repositório e dos resultados registrados nas etapas SDD.

## Estado atual

- **Arquitetura:** [proposta](../architecture.md); os contratos externos e comportamentos futuros ainda dependem de especificações próprias e aprovação.
- **Item 01 — Base de persistência e integração:** [especificação](../specs/base-persistencia-integracao/spec.md) aprovada, implementada, testada e revisada; evidências em suas [tarefas](../specs/base-persistencia-integracao/tasks.md).
- **Item 02 — Domínio de pedidos:** [especificação](../specs/dominio-pedidos/spec.md) aprovada, implementada, testada e revisada. Os resultados estão nas [tarefas](../specs/dominio-pedidos/tasks.md).
- **Item 03 — Modelo relacional:** [spec](../specs/modelo-relacional-pedidos/spec.md) aprovada e implementada; as quatro tabelas e seus mapeamentos foram validados. Evidências nas [tarefas](../specs/modelo-relacional-pedidos/tasks.md). Pedido/itens tiveram revisão anterior; o restante não teve revisão independente, que depende de solicitação.
- **Item 04 — Criação de pedidos:** [spec](../specs/criacao-pedidos/spec.md) aprovada e implementada; API, catálogo HTTP e gravação atômica com Outbox. Contratos/configuração em [order-creation.md](order-creation.md), evidências nas [tarefas](../specs/criacao-pedidos/tasks.md).
- **Itens 05 em diante:** planejados no [roadmap](implementation-roadmap.md), sem publicação/consumo Kafka implementados.

## Implementação encontrada

O projeto contém o bootstrap Spring Boot e configuração JPA com `ddl-auto=validate` e Open Session in View desativado. O perfil `local` configura a conexão PostgreSQL informada pelo usuário e permite substituição pelas variáveis padrão do Spring. O POM declara Spring Data JPA, Flyway, driver PostgreSQL e JaCoCo.

O suporte de testes usa PostgreSQL Testcontainers com imagem `postgres:16.9-alpine`, sem iniciar Kafka. Os testes executam as migrations reais V1/V2 e usam recursos exclusivos de teste para falhas. A configuração, os comandos e os resultados estão em [persistence.md](persistence.md).

O domínio contém `Order` e `OrderItem` mapeados como entidades JPA com Lombok, além de `OrderStatus`, com regras de itens, snapshots, cálculos e transições de reserva. A migration V1 cria `orders` e `order_items`; V2 acrescenta `outbox_messages` e `processed_messages`, mapeadas por `OutboxMessage`, `ProcessedMessage` e chave embutida `ProcessedMessageId`, também com Lombok. Há API `POST /orders`, cliente HTTP de catálogo e serviço de criação com `EntityManager`/`TransactionTemplate`; não foram necessários repositórios adicionais. Não há publicador Outbox ou handler de eventos. Produtos, Estoque e a infraestrutura Kafka seguem indisponíveis conforme o contexto informado anteriormente.

## Decisões e dependências pendentes

- O item 01 foi validado com JDK 25.0.5.0.2, Maven 3.10.0, PostgreSQL Testcontainers 16.9 e PostgreSQL local 16.14.
- Item 02: o usuário aprovou as regras de duas casas decimais sem arredondamento, moeda única, rejeição de produtos repetidos, invariantes dos itens e transições de reserva. A escala uniforme limita moedas que convencionalmente usam outra quantidade de casas.
- Modelo relacional: as quatro tabelas e seus mapeamentos foram autorizados e validados; V1 permanece inalterada e V2 é incremental. Não há ordem de itens persistida ou remoção em cascata; item carregado diretamente precisa resolver a moeda dentro do contexto antes de uso detached.
- API de criação, HTTP de catálogo e produtor local `ReserveStock` foram definidos no item 04. Compatibilidade com Produtos/Estoque reais permanece pendente. Definir publicação, eventos de resultado, deduplicação, cancelamento e pagamento nos respectivos ciclos SDD. Repetir uma criação válida produz outro pedido; autenticação e identificação do comprador estão fora do recorte aprovado.

## Evidências e limites

Na etapa 01, `mvnw.cmd clean verify` passou com três testes e a aplicação iniciou no banco local sem criar tabelas de negócio. O relatório JaCoCo registrou 3 de 8 instruções e 1 de 3 linhas da classe de bootstrap; não há cobertura de negócio nessa medição. A revisão independente foi concluída após correções. Detalhes em [persistence.md](persistence.md).

No domínio, `mvnw.cmd -Dtest=OrderTest test` passou com oito testes, e `mvnw.cmd clean verify` passou com 11 testes no total, sem falhas, erros ou skips. JaCoCo registrou 100% das instruções e linhas das três classes do domínio e 100% dos branches instrumentados de `Order` e `OrderItem`. Isso não comprova integrações futuras. A revisão independente identificou uma lacuna para moeda vazia; ela foi corrigida e reavaliada.

No recorte relacional autorizado, `mvnw.cmd clean verify` passou com 19 testes (8 domínio, 8 persistência, 1 contexto/migration e 2 falhas), sem falhas, erros ou skips. Cobertura de linhas: `OrderItem` 55/61 (90,2%), `Order` 65/69 (94,2%), enum 100%; branches 80% em cada entidade. A revisão reproduziu e corrigiu falha de carga direta do item antes do pedido lazy, depois reavaliou sem novos achados. O JAR contém a migration real e exclui recursos de teste. O banco local não foi acessado nesta etapa.

A conclusão do item 03, em 28/10/2026, passou em `bash ./mvnw clean verify`: 28 testes, zero falhas, erros ou skips, com PostgreSQL Testcontainers 16.9. V1→V2 foi validado com dados existentes preservados, além de round-trip, constraints, duplicatas e rollback de mensagens. JaCoCo da suíte completa: novas classes com 100% das linhas e branches instrumentados (`OutboxMessage` 11/11 linhas e 6/6 branches; `ProcessedMessage` 6/6 linhas, sem branches; `ProcessedMessageId` 6/6 linhas e 4/4 branches). JAR com V1/V2, sem migrations de teste; banco local não acessado. Não houve revisão independente do restante nem validação Kafka/serviços externos.

A etapa 04 passou em `bash ./mvnw clean verify`: **48 testes, zero falhas, erros ou skips**, com catálogo HTTP simulado e PostgreSQL Testcontainers 16.9. Os testes comprovam commit e rollback real de pedido/itens/Outbox, envelope persistido, repetição da criação, erros/timeout do catálogo e validação da API. Medição isolada de 20 testes unitários/slice: classes novas com 100% das linhas; `CatalogClient`/`CatalogProperties` 95% dos branches e `OrderService` 100%. A classe existente `OutboxMessage` tem 83,3% das linhas isoladamente e 100% na suíte agregada, pois os callbacks JPA são exercitados na integração. Relatórios e números por classe nas [tarefas de criação](../specs/criacao-pedidos/tasks.md). Não houve Kafka, serviços reais, banco local ou revisão independente.

## Próximas ações

Iniciar o ciclo SDD do item 05, publicação da Outbox, somente quando solicitado. Especificar política de publicação/repetição/concorrência e apresentar para aprovação antes de implementar. O item 04 apenas persiste mensagens pendentes e não autoriza automaticamente a publicação.
