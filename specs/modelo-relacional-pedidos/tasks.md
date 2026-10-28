# Tarefas — Modelo relacional de pedidos

Estado: **recorte de pedido/itens implementado e validado; mensageria pendente de aprovação**. A proposta completa da [spec](spec.md) continua sem aprovação. Evidências do recorte autorizado abaixo.

## Aprovação e análise

- [x] T01A — Registrar a autorização parcial: o usuário solicitou “transforme o orderItem em uma entidade jpa agora, e use lombok para as anotacoes”; autorizado `OrderItem` e suporte indispensável de `Order`, vínculo, duas tabelas, reidratação e testes. Referências: seção de aprovação parcial da spec e RNF05.
- [ ] T01B — Obter aprovação separada antes de criar tabelas ou mapeamentos de Outbox e processamento. Referências: CA01B, CA06 e RNF05.
- [x] T02 — Conferir alterações existentes, migrations, testes e referências ao domínio; preservar a refatoração e arquivos locais/staged preexistentes. Confirmar as dependências 01 e 02 sem alterar suas evidências históricas. Referências: RNF01, RNF03.

## Implementação após aprovação

- [x] T03A — Criar migration Flyway somente de `orders` e `order_items` com nomes/tipos do diagrama, campos obrigatórios, PKs, FK, unique e checks, incluindo rejeição de escala excessiva e valores não finitos sem coerção monetária. Referências: CA01A, CA04, CA07.
- [ ] T03B — **Não autorizado nesta etapa:** adicionar as tabelas de Outbox e processamento somente após T01B. Referências: CA01B, CA06.
- [x] T04 — Mapear `Order`, converter `OrderItem` para classe JPA e preservar construção validada, cópia dos snapshots, acessores e igualdade de negócio; usar Lombok sem setters ou construtores públicos sem validação. Referências: CA02–CA03.
- [x] T05 — Mapear moeda somente no pedido, relação com itens, cálculo derivado, UUIDs próprios e reidratação sem redefinir estado ou IDs. Não adicionar coluna de posição. Referências: CA02–CA03, CA07.
- [x] T06 — Mapear datas `Instant`, callbacks e `@Version`; configurar cascade apenas de persistência e nenhuma remoção em cascata/orphan removal. Referências: CA05, CA07.
- [ ] T07 — **Não autorizado nesta etapa:** criar mapeamentos mínimos de Outbox e processamento somente após T01B; não adicionar fluxos ou contratos de integração. Referência: CA06.

## Testes e validação

- [x] T08 — Adaptar a suíte da base para executar migrations reais de pedido/itens no PostgreSQL descartável, mantendo recursos exclusivos de falha fora do artefato e sem colisões de versão. Verificar histórico e reexecução. Referências: CA01A, CA08.
- [x] T09 — Testar round-trip de pedido e itens após limpar o contexto, dados e cálculos preservados, moeda derivada, reutilização de entradas sem compartilhar identidade, estados terminais, rejeição de recarga inconsistente e invariantes unitárias. Referências: CA02–CA03.
- [x] T10 — Testar constraints com SQL direto e transações isoladas, incluindo preços válidos e inválidos, nulos, status, PK/unique/FK e ausência de exclusão em cascata. Referências: CA04, CA07.
- [x] T11 — Testar datas e incremento de versão, conflito entre dois contextos e rollback sem perda do primeiro resultado confirmado. Referência: CA05.
- [ ] T12 — **Não autorizado nesta etapa:** testar round-trip de Outbox e processamento após aprovação e implementação dos respectivos modelos. Referência: CA06.
- [x] T13 — Executar `mvnw.cmd clean verify`, conferir JAR e relatórios JaCoCo, registrar comandos, contagem real e cobertura; não substituir testes bloqueados por evidência presumida. Referência: CA08; RNF02–RNF05.
- [x] T14 — Documentar esquema, migrations, execução e limites de exclusão, ordem, SQL direto e integrações futuras. Atualizações do roadmap e estado são responsabilidade do coordenador. Referência: CA08.

## Revisão e encerramento

- [x] T15 — Delegar revisão independente do recorte autorizado ao `code_reviewer`, corrigir achados e repetir a revisão. Conferir CA01A, CA02–CA05, CA07–CA08 e ausência de ampliação para as tabelas pendentes.
- [x] T16 — Registrar arquivos, testes executados, cobertura, riscos e pendências; concluir o recorte autorizado só com evidências e manter CA01B/CA06 e tarefas de mensageria pendentes. Não executar commit, push ou iniciar a funcionalidade seguinte automaticamente.

## Checklist

- [x] Recorte de `OrderItem` JPA com Lombok e suporte indispensável aprovado explicitamente.
- [ ] Proposta completa das quatro tabelas aprovada explicitamente.
- [x] Duas tabelas e mapeamentos de pedido/itens validados no PostgreSQL descartável.
- [ ] Quatro tabelas e mapeamentos validados no PostgreSQL descartável.
- [x] Invariantes e snapshots preservados em memória e após recarga.
- [x] Precisão, constraints e concorrência testadas.
- [x] Build, artefato e cobertura registrados.
- [x] Revisão independente do recorte autorizado concluída e documentação coerente com evidências.

## Evidências do recorte autorizado

- `OrderItem` convertido em classe JPA/Lombok, com UUID próprio, vínculo ao pedido e construtor protegido. `Order` fornece o suporte JPA do agregado, moeda única, timestamps e versão otimista. Sem setters públicos.
- Migration `V1__create_orders_and_order_items.sql` cria somente `orders` e `order_items`. Testes validam schema, round-trip, snapshots, estados, timestamps, conflito otimista, campos obrigatórios, PKs, FK, unicidade e precisão numérica sem arredondamento.
- Regressão reproduziu falha na carga direta de `OrderItem` antes do pai lazy; callbacks e comparação de vínculo foram corrigidos e os testes passaram. Dados inconsistentes na reidratação geram falha identificável.
- `mvnw.cmd clean verify`: 19 testes, zero falhas, erros ou skips (8 domínio, 8 persistência, 1 contexto/migration e 2 falhas de infraestrutura/persistência esperadas).
- JaCoCo por linhas: `OrderItem` 55/61 (90,2%), `Order` 65/69 (94,2%), `OrderStatus` 4/4 (100%); branches 32/40 em cada entidade (80%). Relatórios em `target/site/jacoco/`.
- JAR inclui migration real e exclui migrations de falha de teste. Validação com PostgreSQL Testcontainers, sem acesso a `orders_db` nesta etapa.
- Limites: ordem dos itens não persistida, sem remoção em cascata; moeda de item carregado diretamente deve ser resolvida com contexto aberto antes de uso detached. Detalhes em [persistence.md](../../docs/persistence.md).
- CA01A, CA02–CA05 e CA07–CA08 validados no recorte de pedido/itens; CA01B e CA06 continuam pendentes de aprovação e implementação.


