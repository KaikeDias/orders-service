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

Hibernate usa `ddl-auto=validate` e não cria nem altera tabelas. Flyway executa migrations da localização de produção `classpath:db/migration`; esta etapa ainda não contém migrations de produção. Não habilite `flyway clean`, baseline automático ou reparo para contornar divergências.

## Validar

```powershell
./mvnw.cmd clean verify
```

Os testes usam somente containers PostgreSQL com a imagem fixada em `postgres:16.9-alpine`; não conectam a `orders_db` e não iniciam Kafka. Docker precisa estar disponível. A execução produz relatórios JaCoCo em `target/site/jacoco/index.html` (HTML) e `target/site/jacoco/jacoco.xml` (XML). Sem lógica de negócio nesta etapa, esses relatórios medem somente o código de produção existente e não representam cobertura de regras de negócio.

## Última validação registrada

Em 27/10/2026, `mvnw.cmd clean verify` passou com Maven 3.10.0 e JDK 25.0.5.0.2. Os três testes passaram: um teste de conexão/JPA/Flyway com PostgreSQL Testcontainers 16.9, um teste de conexão inválida e um teste de migration inválida. A migration de teste foi aplicada uma vez e não está no JAR de produção (`target/orders-service-0.0.1-SNAPSHOT.jar`). Os relatórios JaCoCo foram gerados; a classe bootstrap teve 3 de 8 instruções e 1 de 3 linhas cobertas, sem lógica de negócio para medir.

A inicialização com perfil `local` conectou ao PostgreSQL 16.14 em `orders_db` e inicializou JPA. A inspeção somente de leitura após a inicialização encontrou zero tabelas públicas de negócio e zero migrations registradas. Flyway criou sua tabela de histórico. Nenhuma operação de limpeza ou reparo foi executada.
