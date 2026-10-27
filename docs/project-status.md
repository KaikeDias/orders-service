# Estado do projeto — Pedidos

Atualizado em 27/10/2026, por leitura do repositório e elaboração documental.

## Documentação produzida

- [architecture.md](../architecture.md): visão em 14 seções, inventário do estado real, stack declarada, arquitetura proposta, domínio, integrações, sequência, estados, limites transacionais e decisões pendentes.
- Este arquivo: registro de continuidade e verificações efetivamente realizadas.
- O [diagrama de classes existente](diagrams/mermaid-diagram.png) foi inspecionado e referenciado, sem alteração. A seção 7 da arquitetura explica sua reutilização sem duplicação em Mermaid.

## Estado da arquitetura e implementação

**Arquitetura: proposta para revisão.** Os comportamentos de criação, reserva, cancelamento, Outbox e deduplicação permanecem planejados ou propostos, sem aprovação de especificação registrada.

O código encontrado contém bootstrap Spring Boot, configuração com nome da aplicação, teste `contextLoads()`, configuração Testcontainers de Kafka/PostgreSQL e entrada de desenvolvimento com containers. Não foram encontrados domínio de pedidos, API de pedidos, clientes HTTP, repositórios de pedidos, migrations, publicador Outbox ou handler de eventos.

O [POM](../pom.xml) declara Java 25, Spring Boot 4.1.2, Web MVC, Validation, Kafka, Flyway/PostgreSQL, Actuator e dependências de teste. Spring Data JPA é pretendido, mas não está declarado. O wrapper aponta para Maven 3.10.0; resolução e funcionamento não foram verificados.

## Especificação ativa

Nenhuma: a pasta `specs/` e especificações por funcionalidade não foram encontradas. Não foram criados `spec.md` ou `tasks.md` globais. Esta documentação não substitui requisitos aprovados.

## Decisões pendentes

- Revisar a arquitetura e resolver a diferença entre JPA pretendido e POM atual.
- Definir o recorte da primeira funcionalidade e seus contratos HTTP, validações e casos de erro.
- Especificar as regras de cancelamento, liberação e a corrida com `StockReserved` recebido durante cancelamento.
- Definir detalhes monetários e tratamento de produtos ausentes, inativos, repetidos ou com moedas diferentes.
- Acordar com Estoque payloads/versionamento e semântica do envelope, incluindo identificadores para republicação.
- Definir persistência, migrations, polling, concorrência, falhas, offset e retenção para Outbox/deduplicação.
- Decidir reprodutibilidade das tags `latest` dos containers e estratégia de medição da cobertura.
- Especificar pagamento/Stripe, notificações e confirmação definitiva de estoque em evoluções futuras internas de Pedidos.

Todas essas decisões estão detalhadas na [arquitetura](../architecture.md). Não se presume aprovação das transições de cancelamento nem dos contratos futuros de pagamento.

## Verificações efetivamente realizadas

- Leitura do pedido, [AGENTS.md](../AGENTS.md), configuração SDD e configurações dos quatro subagentes.
- Leitura de `pom.xml`, fontes, recursos, testes, arquivos do Maven Wrapper e `HELP.md`; inventário dos arquivos presentes.
- Constatação da ausência inicial de `README.md`, `docs/project-status.md` e `specs/`.
- Inspeção visual do único arquivo encontrado em `docs/diagrams`, `mermaid-diagram.png`.
- Comparação documental entre pedido, diagrama e código: classes de negócio ausentes e dependência Spring Data JPA ausente do POM.
- Consulta Git sem sucesso porque a raiz não é um repositório Git; revisão realizada por leitura de arquivos, sem diff Git.
- Conferência dos 28 links locais dos dois documentos, todos com destinos existentes, das 14 seções numeradas da arquitetura e dos delimitadores dos quatro blocos Mermaid. Renderização Mermaid não executada.

**Não executados:** build, testes, aplicação, containers, banco, Kafka ou validação de integração. **Cobertura:** não medida. O teste `contextLoads()` existe, mas não há resultado verificado nesta tarefa.

## Próxima ação concreta

Submeter `../architecture.md` à revisão do usuário, destacando decisões pendentes e a reutilização do PNG na seção 7. Após a revisão, definir a primeira funcionalidade e delegar ao arquiteto sua especificação em `specs/<funcionalidade-kebab-case>/spec.md` e `tasks.md`. Obter aprovação explícita dessa especificação antes da implementação, conforme [AGENTS.md](../AGENTS.md).

Nenhuma implementação, alteração de dependência/configuração de execução, commit, push ou Merge Request integra esta entrega documental.
