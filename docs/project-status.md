# Estado do projeto — Pedidos

Atualizado em 27/10/2026, a partir dos arquivos do repositório e dos resultados registrados nas etapas SDD.

## Estado atual

- **Arquitetura:** [proposta](../architecture.md); os contratos externos e comportamentos futuros ainda dependem de especificações próprias e aprovação.
- **Item 01 — Base de persistência e integração:** [especificação](../specs/base-persistencia-integracao/spec.md) aprovada, implementada, testada e revisada; evidências em suas [tarefas](../specs/base-persistencia-integracao/tasks.md).
- **Item 02 — Domínio de pedidos:** [especificação](../specs/dominio-pedidos/spec.md) aprovada, implementada, testada e revisada. Os resultados estão nas [tarefas](../specs/dominio-pedidos/tasks.md).
- **Itens 03 em diante:** planejados no [roadmap](implementation-roadmap.md), ainda sem implementação de negócio.

## Implementação encontrada

O projeto contém o bootstrap Spring Boot e configuração JPA com `ddl-auto=validate` e Open Session in View desativado. O perfil `local` configura a conexão PostgreSQL informada pelo usuário e permite substituição pelas variáveis padrão do Spring. O POM declara Spring Data JPA, Flyway, driver PostgreSQL e JaCoCo.

O suporte de testes usa PostgreSQL Testcontainers com imagem `postgres:16.9-alpine`, sem iniciar Kafka. Há migrations exclusivas de teste e testes de conexão, migration e falhas. A configuração, os comandos e os resultados estão em [persistence.md](persistence.md).

O domínio contém `Order`, `OrderItem` e `OrderStatus`, com regras de itens, snapshots, cálculos e transições de reserva. Não foram encontradas API de pedidos, clientes de catálogo, repositórios de pedidos, migrations de negócio, publicador Outbox ou handler de eventos. Produtos, Estoque e a infraestrutura Kafka seguem indisponíveis conforme o contexto informado anteriormente.

## Decisões e dependências pendentes

- O item 01 foi validado com JDK 25.0.5.0.2, Maven 3.10.0, PostgreSQL Testcontainers 16.9 e PostgreSQL local 16.14.
- Item 02: o usuário aprovou as regras de duas casas decimais sem arredondamento, moeda única, rejeição de produtos repetidos, invariantes dos itens e transições de reserva. A escala uniforme limita moedas que convencionalmente usam outra quantidade de casas.
- Definir posteriormente os contratos de API, catálogo e eventos de Estoque, além das regras de cancelamento, pagamento, Outbox e deduplicação. A aprovação de uma especificação não aprova automaticamente os itens seguintes.

## Evidências e limites

Na etapa 01, `mvnw.cmd clean verify` passou com três testes e a aplicação iniciou no banco local sem criar tabelas de negócio. O relatório JaCoCo registrou 3 de 8 instruções e 1 de 3 linhas da classe de bootstrap; não há cobertura de negócio nessa medição. A revisão independente foi concluída após correções. Detalhes em [persistence.md](persistence.md).

No domínio, `mvnw.cmd -Dtest=OrderTest test` passou com oito testes, e `mvnw.cmd clean verify` passou com 11 testes no total, sem falhas, erros ou skips. JaCoCo registrou 100% das instruções e linhas das três classes do domínio e 100% dos branches instrumentados de `Order` e `OrderItem`. Isso não comprova integrações futuras. A revisão independente identificou uma lacuna para moeda vazia; ela foi corrigida e reavaliada.

## Próximas ações

O próximo item planejado é a criação de pedidos com catálogo e Outbox. Sua especificação e eventual implementação exigem um novo ciclo SDD e aprovação explícita, conforme [AGENTS.md](../AGENTS.md).
