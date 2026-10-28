# Persistência local

## Pré-requisitos

- JDK 25 e Maven Wrapper do projeto.
- PostgreSQL disponível em `localhost:5432` e o banco `orders_db` previamente criado para execução local.
- Docker para os testes de integração, que usam um PostgreSQL descartável.

## Executar localmente

Ative o perfil explicitamente ao iniciar a aplicação:

```powershell
$env:JAVA_HOME = 'C:\caminho\para\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
./mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

O perfil `local` usa `jdbc:postgresql://localhost:5432/orders_db` e as credenciais locais configuradas em `application-local.yaml`. Sobrescreva a conexão com as variáveis padrão do Spring `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. Outros ambientes devem fornecer sua própria conexão; o perfil local não é ativado automaticamente.

Hibernate usa `ddl-auto=validate` e não cria nem altera tabelas. Flyway executa migrations da localização de produção `classpath:db/migration`. O modelo atual cria `orders` e `order_items` pela V1 e `outbox_messages` e `processed_messages` pela V2, aprovada no item 03. V1 permanece inalterada. Não habilite `flyway clean`, baseline automático ou reparo para contornar divergências.

## Validar

```powershell
./mvnw.cmd clean verify
```

Os testes usam somente containers PostgreSQL com a imagem fixada em `postgres:16.9-alpine`; não conectam a `orders_db` e não iniciam Kafka. Docker precisa estar disponível. A execução produz relatórios JaCoCo em `target/site/jacoco/index.html` (HTML) e `target/site/jacoco/jacoco.xml` (XML). A cobertura inclui domínio, persistência e criação de pedidos com catálogo HTTP simulado. Fluxos Kafka permanecem fora desta etapa.

## Validação histórica do recorte de pedido/itens

Em 27/10/2026, `mvnw.cmd clean verify` passou com Maven 3.10.0 e JDK 25.0.5.0.2: 19 testes, sem falhas, erros ou skips. Os testes de persistência usaram PostgreSQL Testcontainers 16.9 e verificaram a migration real, round-trip do agregado, carregamento direto de item com associação lazy, constraints SQL incluindo nulos obrigatórios e IDs duplicados, timestamps, concorrência otimista, reidratação inválida e ausência de remoção em cascata. As migrations de falha de teste não estão no JAR de produção (`target/orders-service-0.0.1-SNAPSHOT.jar`).

JaCoCo gerou HTML/XML. A cobertura de linhas foi: `OrderItem` 55/61 (90,2%), `Order` 65/69 (94,2%) e `OrderStatus` 4/4 (100%); branches foram 32/40 (80%) em `OrderItem`, 32/40 (80%) em `Order` e sem branches em `OrderStatus`.

A validação deste modelo usou exclusivamente PostgreSQL Testcontainers; `orders_db` não foi acessado. A migration parcial contém apenas `orders` e `order_items`, além do histórico Flyway. `outbox_messages` e `processed_messages` não foram criadas. Nenhuma operação de limpeza ou reparo foi executada.

Ao carregar `OrderItem` diretamente, a moeda é obtida pela associação lazy ao pedido. Resolva `currency()` enquanto o item permanece associado a um contexto de persistência aberto. Ao recarregar o agregado `Order`, a moeda dos itens é reconstruída durante a validação do agregado.

## Conclusão do modelo relacional — 28/10/2026

`bash ./mvnw clean verify` passou com Maven 3.10.0 e Amazon Corretto 25.0.4.1: **28 testes, zero falhas, erros ou skips**. Foram usados exclusivamente containers PostgreSQL `postgres:16.9-alpine`; o banco `orders_db` não foi acessado. O JAR foi inspecionado e contém V1/V2 de produção, sem migrations de falha de teste.

A V2 cria Outbox e registros de processamento sem FKs externas. `OutboxMessage` mantém payload textual opaco, UUID gerado pela aplicação, data de criação e publicação inicialmente nula. `ProcessedMessage` usa `ProcessedMessageId` embutido com `(consumer, message_id)`, preservando o UUID original. As entidades seguem Lombok, getters fluentes e construtores JPA protegidos, sem setters públicos. A unicidade física não implementa deduplicação operacional.

A suíte verifica banco vazio, upgrade V1→V2 com dados existentes preservados, histórico e não reaplicação, round-trip após flush/clear, texto Unicode/não JSON, datas, nulos obrigatórios, duplicatas e rollback de toda a transação. V1 foi conferida byte a byte contra `HEAD`. Ao usar schema isolado no teste, a conexão restaura o schema anterior antes de retornar ao pool.

JaCoCo da suíte completa: `OutboxMessage` 11/11 linhas e 6/6 branches (100%); `ProcessedMessage` 6/6 linhas (100%, sem branches); `ProcessedMessageId` 6/6 linhas e 4/4 branches (100%). Callbacks de datas são exercitados na integração JPA, portanto a medição agrega testes unitários e de integração. Cobertura de pedido/itens permaneceu nos valores históricos acima.

Não foram executados publicador, consumidor, catálogo ou Kafka; a atualização de `published_at` foi comprovada por SQL seguido de recarga, sem operação pública de publicação. Timestamps são comparados com tolerância de um microssegundo para a precisão PostgreSQL. Não houve revisão independente deste restante, pois depende de solicitação do usuário.

## Criação transacional de pedidos — item 04

A API de criação usa `EntityManager` e `TransactionTemplate` para persistir pedido, itens e Outbox juntos. O catálogo HTTP e a serialização ocorrem antes da transação; a resposta só é devolvida após commit. Não há nova migration: V1/V2 são preservadas. Contratos, variáveis de configuração e limites estão em [order-creation.md](order-creation.md).

Os testes de integração usam PostgreSQL Testcontainers com Open Session in View desativado e `ddl-auto=validate`. Uma trigger exclusiva do banco descartável confirma a presença de pedido/itens já flushados, avança uma sequência (que não reverte com rollback) e força falha de inserção da Outbox. Após o erro, o teste verifica a sequência e ausência de novas linhas, preservando dados anteriores. Nenhuma trigger ou sequência de teste integra o artefato de produção.

Validação final do item 04: `bash ./mvnw clean verify`, 48 testes sem falhas, erros ou skips. JAR inspecionado com somente V1/V2 de produção; ambas preservadas byte a byte. Cobertura agregada em `target/site/jacoco/`; medição isolada de unitários/slice preservada em `target/site/jacoco-unit/jacoco.xml`. Valores por unidade e limites nas [tarefas](../specs/criacao-pedidos/tasks.md).
