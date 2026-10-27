# Base de persistência e integração

Estado: **aprovada, implementada e validada**. Item 01 do [roadmap](../../docs/implementation-roadmap.md). Aprovação registrada pela solicitação explícita: “implemente spec.md”; evidências em [persistence.md](../../docs/persistence.md) e [tasks.md](tasks.md).

## Objetivo

Preparar a aplicação para PostgreSQL, Spring Data JPA e Flyway, com configuração local utilizável e testes de integração reproduzíveis. Não introduzir o domínio de pedidos nem depender de Kafka, Produtos ou Estoque.

## Contexto e evidências

- A aplicação iniciou com perfil local e conectou ao `orders_db` em PostgreSQL 16.14; não foram encontradas tabelas públicas de negócio.
- `pom.xml` declara Java 25, Spring Boot 4.1.2, Spring Data JPA, PostgreSQL, Flyway, Testcontainers e JaCoCo. A stack foi resolvida e o build passou com JDK 25.0.5.0.2 e Maven Wrapper 3.10.0.
- `application.properties` configura JPA; a conexão local fica no perfil explícito `local`. Não há entidades ou migrations de produção nesta etapa.
- Os testes usam PostgreSQL `16.9-alpine`, sem iniciar Kafka, e contêm migrations isoladas de teste.

## Regras de negócio e limites de dados

Esta etapa não define regras de pedidos. O banco informado deve ser preservado: não executar limpeza, reset, `Flyway clean`, criação automática de tabelas pelo Hibernate ou migração de tabelas de negócio. A aplicação conecta a um banco previamente criado; não cria o banco PostgreSQL.

## Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF01 | Manter o starter Spring Data JPA já declarado no POM, usando o gerenciamento de dependências do Spring Boot. Demonstrar sua resolução e compatibilidade com Java 25 e a stack declarada. Uma incompatibilidade deve ser relatada com evidência e proposta de ajuste antes de mudar a stack. |
| RF02 | Permitir iniciar a aplicação com perfil explícito `local` usando a URL `jdbc:postgresql://localhost:5432/orders_db` e as credenciais locais informadas. Aceitar substituições pelos mecanismos padrão do Spring, incluindo `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. Os valores locais devem ficar restritos ao perfil `local`; os demais ambientes fornecem sua própria conexão. Não ativar esse perfil automaticamente. |
| RF03 | Manter Flyway como responsável pelas futuras alterações de esquema; configurar Hibernate com `ddl-auto=validate` e desativar Open Session in View. Não adicionar migration fictícia de produção nem tabelas de negócio. A ausência de migrations de produção é intencional nesta etapa e deve ser documentada. |
| RF04 | Demonstrar execução e controle de migrations em PostgreSQL descartável por uma migration exclusiva de teste, sem DDL de negócio, por exemplo `SELECT 1`. Usar localização de teste explícita, fora da localização de migrations de produção. Verificar seu registro de sucesso e a ausência de reaplicação em uma segunda execução do Flyway no mesmo banco. |
| RF05 | Adaptar o suporte de teste e a entrada de desenvolvimento com Testcontainers para precisarem somente de PostgreSQL nesta etapa. Não iniciar Kafka para validar persistência; preservar as dependências Kafka já declaradas para as etapas futuras. Usar imagem PostgreSQL com versão explícita, sem `latest`, escolhida após conferir compatibilidade com as dependências resolvidas e registrada na documentação. |
| RF06 | Disponibilizar relatório JaCoCo HTML e XML no ciclo Maven `verify`, com versão compatível com Java 25. Informar resultados e limites reais; não introduzir meta global de cobertura nesta etapa sem unidades de negócio. |
| RF07 | Documentar pré-requisitos, seleção de JDK 25, execução local, configuração por ambiente, comandos de build/testes, localização dos relatórios e dependência de Docker para os testes. |

## Requisitos não funcionais

- **RNF01 — Isolamento:** testes automatizados usam exclusivamente PostgreSQL descartável de Testcontainers, inclusive para cenários de falha. Não usar nem modificar `orders_db` nos testes.
- **RNF02 — Reprodutibilidade:** o wrapper e as dependências devem resolver e o build deve compilar com JDK 25. Registrar versões efetivamente usadas e imagem PostgreSQL escolhida. Não atualizar componentes sem necessidade demonstrada.
- **RNF03 — Falhas visíveis:** conexão inválida ou migração inválida impede a inicialização do contexto correspondente. Não mascarar a falha desabilitando Flyway/JPA, trocando PostgreSQL por H2 ou pulando silenciosamente os testes quando Docker estiver indisponível.
- **RNF04 — Segurança operacional:** não adicionar credenciais de outros ambientes, não expor senhas em documentação de execução ou relatórios, e não ampliar endpoints Actuator. As credenciais locais informadas são exclusivamente de desenvolvimento.
- **RNF05 — Cobertura honesta:** medir o código de produção instrumentável existente. Sem lógica própria nova, um percentual de bootstrap não demonstra cobertura de negócio. Se houver unidade de código própria implementada, buscar ao menos 90% de cobertura unitária por unidade, conforme a orientação do engenheiro, e registrar a medição ou lacuna.

## Critérios de aceitação

| ID | Cenário e evidência exigida | Requisitos |
| --- | --- | --- |
| CA01 | Com JDK 25 e dependências acessíveis, o wrapper resolve a stack e `mvnw.cmd clean verify` termina com sucesso, incluindo compilação, testes e relatórios JaCoCo. Registrar as versões usadas. | RF01, RF06; RNF02 |
| CA02 | Com PostgreSQL local disponível, iniciar a aplicação com perfil `local` e comprovar conexão ao `orders_db` e inicialização de JPA/Flyway. Confirmar que não foram criadas tabelas de negócio nem executadas operações destrutivas. Se o serviço estiver indisponível, registrar esta validação como pendente, sem substituí-la pela evidência de containers. | RF02, RF03 |
| CA03 | Em teste automatizado com PostgreSQL descartável, o contexto inicia com `DataSource`, `EntityManagerFactory` e Flyway disponíveis, e uma consulta JDBC simples funciona. A conexão do container prevalece sobre os valores locais. | RF01, RF02, RF05; RNF01 |
| CA04 | No banco descartável, a migration exclusiva de teste consta como aplicada com sucesso no histórico do Flyway; nova execução não a reaplica. Confirmar que o recurso de teste não integra o artefato de produção. | RF03, RF04 |
| CA05 | Um teste com configuração de conexão inválida e outro com migration inválida demonstram falha de inicialização/migração, usando recursos exclusivos de teste. Nenhum deles acessa o banco local. | RF03, RF04; RNF01, RNF03 |
| CA06 | A suíte de persistência e a entrada de desenvolvimento com containers não iniciam Kafka. A imagem PostgreSQL tem versão explícita. Sem Docker, a execução reporta falha de infraestrutura e não é apresentada como validação aprovada. | RF05; RNF02, RNF03 |
| CA07 | A documentação permite reproduzir execução e validação; informa configuração por ambiente, ausência intencional de migrations de produção, caminhos JaCoCo e resultados/pendências de cobertura. | RF06, RF07; RNF04, RNF05 |

## Casos de erro

| Caso | Resultado esperado |
| --- | --- |
| Banco ausente, porta inacessível ou credenciais incorretas | Inicialização falha com causa de conexão identificável, sem fallback para outro banco. |
| Migração inválida | Flyway reporta falha; o contexto não é considerado pronto. Não executar reparo ou limpeza automática. |
| JDK inadequado, distribuição Maven ou dependência não resolvível | Registrar bloqueio e diagnóstico; propor a correção mínima ao coordenador antes de trocar a stack. |
| Docker indisponível ou imagem não obtida | Registrar impedimento dos testes; não declarar sucesso nem utilizar o banco local como alternativa. |
| Banco local contém tabelas preexistentes | Preservar os dados; não ativar baseline, reparo ou limpeza automaticamente para contornar incompatibilidades. Relatar a condição. |

## Fora de escopo

Entidades, repositórios e tabelas de pedidos; Outbox; deduplicação; endpoints de negócio; contratos HTTP/Kafka; provisionamento de banco, Docker ou Kafka; CI/CD; atualização generalizada de bibliotecas; migrations de produção sem necessidade de esquema; teste com os microsserviços externos.

## Decisões aprovadas

Adotar JPA agora; usar perfil `local` explícito e configuração externa nos demais ambientes; comprovar migrations com recurso exclusivo de teste; retirar a necessidade de Kafka do suporte de persistência; medir cobertura com JaCoCo sem gate global artificial. A versão exata da imagem e do plugin de cobertura é uma escolha técnica condicionada à compatibilidade demonstrada na implementação.

Não há regra de negócio pendente nesta etapa. Falhas de resolução da stack ou condições inesperadas do banco devem retornar ao coordenador, sem autorizar mudanças implícitas de requisito.
