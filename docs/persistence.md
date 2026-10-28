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

O perfil `local` usa `jdbc:postgresql://localhost:5432/orders_db` e as credenciais locais configuradas em `application-local.properties`. Sobrescreva a conexão com as variáveis padrão do Spring `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. Outros ambientes devem fornecer sua própria conexão; o perfil local não é ativado automaticamente.

Hibernate usa `ddl-auto=validate` e não cria nem altera tabelas. Flyway executa migrations da localização de produção `classpath:db/migration`. O modelo parcial atual cria apenas `orders` e `order_items`; tabelas de Outbox e processamento permanecem pendentes de aprovação. Não habilite `flyway clean`, baseline automático ou reparo para contornar divergências.

## Validar

```powershell
./mvnw.cmd clean verify
```

Os testes usam somente containers PostgreSQL com a imagem fixada em `postgres:16.9-alpine`; não conectam a `orders_db` e não iniciam Kafka. Docker precisa estar disponível. A execução produz relatórios JaCoCo em `target/site/jacoco/index.html` (HTML) e `target/site/jacoco/jacoco.xml` (XML). A cobertura inclui regras de domínio e persistência; APIs, catálogo e fluxos Kafka continuam fora desta etapa.

## Última validação registrada

Em 27/10/2026, `mvnw.cmd clean verify` passou com Maven 3.10.0 e JDK 25.0.5.0.2: 19 testes, sem falhas, erros ou skips. Os testes de persistência usaram PostgreSQL Testcontainers 16.9 e verificaram a migration real, round-trip do agregado, carregamento direto de item com associação lazy, constraints SQL incluindo nulos obrigatórios e IDs duplicados, timestamps, concorrência otimista, reidratação inválida e ausência de remoção em cascata. As migrations de falha de teste não estão no JAR de produção (`target/orders-service-0.0.1-SNAPSHOT.jar`).

JaCoCo gerou HTML/XML. A cobertura de linhas foi: `OrderItem` 55/61 (90,2%), `Order` 65/69 (94,2%) e `OrderStatus` 4/4 (100%); branches foram 32/40 (80%) em `OrderItem`, 32/40 (80%) em `Order` e sem branches em `OrderStatus`.

A validação deste modelo usou exclusivamente PostgreSQL Testcontainers; `orders_db` não foi acessado. A migration parcial contém apenas `orders` e `order_items`, além do histórico Flyway. `outbox_messages` e `processed_messages` não foram criadas. Nenhuma operação de limpeza ou reparo foi executada.

Ao carregar `OrderItem` diretamente, a moeda é obtida pela associação lazy ao pedido. Resolva `currency()` enquanto o item permanece associado a um contexto de persistência aberto. Ao recarregar o agregado `Order`, a moeda dos itens é reconstruída durante a validação do agregado.
