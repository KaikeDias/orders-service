# Estado do projeto — Pedidos

Atualizado em 27/10/2026, a partir dos arquivos do repositório e dos resultados registrados nas etapas SDD.

## Estado atual

- **Arquitetura:** [proposta](../architecture.md); os contratos externos e comportamentos futuros ainda dependem de especificações próprias e aprovação.
- **Item 01 — Base de persistência e integração:** [especificação](../specs/base-persistencia-integracao/spec.md) aprovada, implementada, testada e revisada; evidências em suas [tarefas](../specs/base-persistencia-integracao/tasks.md).
- **Item 02 — Domínio de pedidos:** [especificação](../specs/dominio-pedidos/spec.md) aprovada, implementada, testada e revisada. Os resultados estão nas [tarefas](../specs/dominio-pedidos/tasks.md).
- **Item 03 — Modelo relacional:** [spec](../specs/modelo-relacional-pedidos/spec.md) parcialmente autorizada: `OrderItem` JPA e suporte de pedido/itens implementados, testados e revisados. Outbox e processamento continuam pendentes de aprovação e implementação, conforme as [tarefas](../specs/modelo-relacional-pedidos/tasks.md); o item completo permanece parcial.
- **Itens 04 em diante:** planejados no [roadmap](implementation-roadmap.md), ainda sem implementação de negócio.

## Implementação encontrada

O projeto contém o bootstrap Spring Boot e configuração JPA com `ddl-auto=validate` e Open Session in View desativado. O perfil `local` configura a conexão PostgreSQL informada pelo usuário e permite substituição pelas variáveis padrão do Spring. O POM declara Spring Data JPA, Flyway, driver PostgreSQL e JaCoCo.

O suporte de testes usa PostgreSQL Testcontainers com imagem `postgres:16.9-alpine`, sem iniciar Kafka. Os testes executam a migration real de pedido/itens e usam recursos exclusivos de teste para falhas. A configuração, os comandos e os resultados estão em [persistence.md](persistence.md).

O domínio contém `Order` e `OrderItem` mapeados como entidades JPA com Lombok, além de `OrderStatus`, com regras de itens, snapshots, cálculos e transições de reserva. A migration V1 cria `orders` e `order_items`. Não foram encontradas API de pedidos, clientes de catálogo, repositórios de pedidos, publicador Outbox ou handler de eventos. Produtos, Estoque e a infraestrutura Kafka seguem indisponíveis conforme o contexto informado anteriormente.

## Decisões e dependências pendentes

- O item 01 foi validado com JDK 25.0.5.0.2, Maven 3.10.0, PostgreSQL Testcontainers 16.9 e PostgreSQL local 16.14.
- Item 02: o usuário aprovou as regras de duas casas decimais sem arredondamento, moeda única, rejeição de produtos repetidos, invariantes dos itens e transições de reserva. A escala uniforme limita moedas que convencionalmente usam outra quantidade de casas.
- Modelo relacional: pedido/itens foram autorizados e validados. As duas tabelas de mensageria e seus mapeamentos aguardam aprovação. Não há ordem de itens persistida ou remoção em cascata; item carregado diretamente precisa resolver a moeda dentro do contexto antes de uso detached.
- Definir posteriormente os contratos de API, catálogo e eventos de Estoque, além das regras de cancelamento, pagamento, Outbox e deduplicação. A aprovação de uma especificação não aprova automaticamente os itens seguintes.

## Evidências e limites

Na etapa 01, `mvnw.cmd clean verify` passou com três testes e a aplicação iniciou no banco local sem criar tabelas de negócio. O relatório JaCoCo registrou 3 de 8 instruções e 1 de 3 linhas da classe de bootstrap; não há cobertura de negócio nessa medição. A revisão independente foi concluída após correções. Detalhes em [persistence.md](persistence.md).

No domínio, `mvnw.cmd -Dtest=OrderTest test` passou com oito testes, e `mvnw.cmd clean verify` passou com 11 testes no total, sem falhas, erros ou skips. JaCoCo registrou 100% das instruções e linhas das três classes do domínio e 100% dos branches instrumentados de `Order` e `OrderItem`. Isso não comprova integrações futuras. A revisão independente identificou uma lacuna para moeda vazia; ela foi corrigida e reavaliada.

No recorte relacional autorizado, `mvnw.cmd clean verify` passou com 19 testes (8 domínio, 8 persistência, 1 contexto/migration e 2 falhas), sem falhas, erros ou skips. Cobertura de linhas: `OrderItem` 55/61 (90,2%), `Order` 65/69 (94,2%), enum 100%; branches 80% em cada entidade. A revisão reproduziu e corrigiu falha de carga direta do item antes do pedido lazy, depois reavaliou sem novos achados. O JAR contém a migration real e exclui recursos de teste. O banco local não foi acessado nesta etapa.

## Próximas ações

Revisar e aprovar o restante da [spec do modelo relacional](../specs/modelo-relacional-pedidos/spec.md), relativo a Outbox e processamento. A criação de pedidos com catálogo e Outbox depende da conclusão desse modelo, conforme o roadmap atualizado e [AGENTS.md](../AGENTS.md).
