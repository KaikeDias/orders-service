# Tarefas — Base de persistência e integração

Estado: **concluída com validação e revisão independente**. O usuário aprovou explicitamente a [especificação](spec.md). Evidências de execução em [persistence.md](../../docs/persistence.md).

## Aprovação e pré-requisitos

- [x] T01 — Coordenador apresenta a proposta e registra aprovação explícita do usuário antes de delegar implementação. Evidência: solicitação do usuário “implemente spec.md” em 27/10/2026. Referência: processo SDD e todos os critérios.
- [x] T02 — Verificar estado Git, JDK, wrapper, resolução e Docker. Evidência: JDK 25.0.5.0.2 e wrapper Maven 3.10.0; stack resolvida e Docker utilizado após acesso ampliado. Referências: CA01, CA06.

## Implementação após aprovação

- [x] T03 — JPA resolvido; `ddl-auto=validate` e Open Session in View desativado. Referências: CA01, CA03.
- [x] T04 — Perfil `local` e configuração externa padrão implementados; inicialização local validada. Referências: CA02, CA03, CA07.
- [x] T05 — Testcontainers somente PostgreSQL, imagem `postgres:16.9-alpine`; dependências Kafka preservadas. Referência: CA06.
- [x] T06 — JaCoCo 0.8.14 produz HTML e XML em `verify`, sem gate global. Referências: CA01, CA07.

## Testes e validação

- [x] T07 — Teste de contexto confirmou JDBC, JPA e Flyway no PostgreSQL descartável. Referência: CA03.
- [x] T08 — Migration de teste aplicada uma vez, não reaplicada e ausente do JAR de produção. Referência: CA04.
- [x] T09 — Testes de conexão e migration inválidas passaram isolados do banco local. Referência: CA05.
- [x] T10 — `mvnw.cmd clean verify` passou com JDK 25 e Docker: três testes, sem falhas, erros ou skips; relatórios JaCoCo gerados. Referências: CA01, CA03–CA06.
- [x] T11 — Perfil `local` iniciou com JPA/Flyway no `orders_db`, PostgreSQL 16.14; nenhuma tabela de negócio criada. Referência: CA02.
- [x] T12 — Documentação e resultados registrados em [persistence.md](../../docs/persistence.md). Referência: CA07.

## Revisão e encerramento

- [x] T13 — Revisão independente realizada e achados corrigidos; reavaliação sem novos defeitos. Referência: CA01–CA07.
- [x] T14 — Evidências de critérios, testes, cobertura e limitações registradas em [persistence.md](../../docs/persistence.md) e comunicadas ao usuário. Referência: CA01–CA07.

## Checklist de evidências

- [x] Aprovação explícita registrada em 27/10/2026.
- [x] JDK, Maven e dependências resolvidos e compatíveis em execução.
- [x] Build e testes aprovados, incluindo os cenários de erro.
- [x] Conexão local validada e dados preservados.
- [x] Migrations demonstradas somente no banco descartável; artefato de produção conferido.
- [x] PostgreSQL de teste versionado e ausência de dependência de Kafka comprovados.
- [x] Relatórios de cobertura gerados e limitações registradas.
- [x] Documentação e revisão independente concluídas.

## Histórico da validação

- Inicialmente Maven Central e Docker estavam inacessíveis no ambiente restrito e `localhost:5432` não respondia. Posteriormente o acesso ampliado permitiu o build e os containers, e o PostgreSQL local estava disponível.
- JaCoCo mediu apenas o bootstrap: 3 de 8 instruções e 1 de 3 linhas. Essa medição não representa cobertura de negócio.
