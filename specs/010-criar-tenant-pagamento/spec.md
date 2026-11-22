# Feature Specification: Criar o Tenant na confirmação de pagamento (Asaas simulado)

**Feature Branch**: `LLT-289-criar-o-tenant-na-confirmacao-de-pagamento-asaas-simulado`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "Solução temporária para a criação do Tenant enquanto o pagamento não está implementado. O gateway será o Asaas. Implementar como será, mais ou menos, a integração com o Asaas para a criação real do Tenant e, enquanto ela não existe, deixar mockado algo que receba a confirmação do Asaas e crie o Tenant. Todo plano passa pela confirmação, inclusive o gratuito. Nesta etapa, só a ativação." (LLT-289)

## Clarifications

### Session 2026-11-18

- Q: Usar a LLT-157 ou uma tarefa nova? → A: Tarefa nova, LLT-289, no épico LLT-109 (Assinatura e cobrança).
- Q: A Assinatura criada aqui é a do Tenant (ADR_122) ou a `PlanSubscription` por produto do módulo de uso? → A: Decisão delegada. Fica a Assinatura do Tenant (ADR_122): um documento por Tenant, com plano e status. A `PlanSubscription` por produto (LLT-183/185) não é alterada; a ligação entre as duas fica para o plano e para o alinhamento com o Michel.
- Q: O plano gratuito ativa direto? → A: Não. Todo plano, inclusive o gratuito, só ativa depois da confirmação do gateway.
- Q: Escopo desta etapa? → A: Só a ativação. Atraso de pagamento, inadimplência, troca de plano, cancelamento e reembolso ficam fora.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contratar um plano e entrar no sistema depois da confirmação (Priority: P1)

O administrador cria a organização da empresa na Clerk. Ela ainda não tem Tenant. Ele escolhe um plano e segue para o pagamento. Quando o gateway confirma o pagamento, o Core cria o Tenant ativo, a Assinatura com o plano escolhido e registra o administrador como DONO. A partir daí, ele e os membros da organização usam Empresa, Produtos e Equipe normalmente. Nesta etapa o gateway é um simulador local do Asaas: a página de pagamento tem um botão "Simular pagamento", e a confirmação chega ao Core pelo mesmo caminho e no mesmo formato que a do Asaas real.

**Why this priority**: Hoje nenhuma organização nova consegue entrar. O único caminho é o seed local (`DEV_TENANT_CLERK_ORG_ID`), que exige editar o `.env` e reiniciar o Core. Sem esta história, o Tenant não tem como nascer na compra, como define a ADR_121.

**Independent Test**: Criar uma organização nova na instância de desenvolvimento da Clerk, sem colocá-la no `.env`, contratar um plano pelo simulador e abrir `/empresa` sem ser deslogado.

**Acceptance Scenarios**:

1. **Given** um administrador com uma organização sem Tenant, **When** ele pede a contratação de um plano, **Then** o Core registra um pedido pendente para aquela organização e plano e devolve o endereço da página de pagamento.
2. **Given** um pedido pendente, **When** o gateway confirma o pagamento dele, **Then** o Core cria um Tenant ativo para a organização, uma Assinatura ativa com o plano do pedido e o Membro DONO para quem fez o pedido, e marca o pedido como pago.
3. **Given** o Tenant criado pela confirmação, **When** o administrador ou outro membro da organização chama o Core, **Then** o acesso é concedido como para qualquer Tenant ativo.
4. **Given** o plano gratuito, **When** o administrador o escolhe, **Then** o fluxo é o mesmo: o Tenant só nasce depois da confirmação do gateway.
5. **Given** um pedido pendente, **When** a confirmação ainda não chegou, **Then** a organização continua sem Tenant e sem acesso às demais funções.

---

### User Story 2 - Organização sem Tenant é levada à contratação, sem ser deslogada (Priority: P1)

Uma pessoa entra com uma organização que ainda não tem Tenant. Hoje o Core responde 403 de segurança, revoga as sessões e o Web volta ao login sem explicação. Com esta feature, o Core permite o que é necessário para contratar (ver os planos, pedir a contratação e acompanhar o pedido) e, nas demais rotas, responde com uma recusa de negócio com código próprio. O Web usa esse código para mostrar a tela de planos em vez de encerrar a sessão.

**Why this priority**: Sem isso o administrador não consegue nem chegar à tela de contratação: a primeira chamada ao Core já o desloga. É pré-requisito da User Story 1.

**Independent Test**: Entrar com uma organização sem Tenant e chamar uma rota de Empresa: a resposta é a recusa com o código de contratação pendente, sem revogação de sessão; a lista de planos responde normalmente.

**Acceptance Scenarios**:

1. **Given** uma sessão válida com organização ativa e sem Tenant, **When** a pessoa pede a lista de planos ou o estado do pedido da organização, **Then** o Core responde normalmente.
2. **Given** a mesma sessão, **When** a pessoa chama qualquer outra rota do Core, **Then** a resposta é 403 com o código `subscription_required` e uma mensagem, e as sessões dela na Clerk não são revogadas.
3. **Given** a mesma sessão, mas de um membro que não é administrador da organização, **When** ele pede a contratação, **Then** recebe a recusa de negócio `not_admin` e continua logado.
4. **Given** uma sessão sem organização ativa, com papel desconhecido ou com Tenant suspenso, **When** chama o Core, **Then** o comportamento atual se mantém: 403 de segurança e revogação.
5. **Given** um token ausente, inválido ou expirado, **When** chega a qualquer rota, inclusive as de contratação, **Then** a resposta continua sendo 401.

---

### User Story 3 - Confirmações repetidas, falsas ou com falha não deixam o Tenant errado (Priority: P2)

O gateway pode enviar a mesma confirmação mais de uma vez, fora de ordem ou com atraso. Alguém pode tentar enviar uma confirmação falsa. A criação pode falhar no meio por um problema no banco. Em todos os casos, cada organização termina com no máximo um Tenant, e nada é criado sem uma confirmação autêntica.

**Why this priority**: Garante a regra de segurança e de cobrança (acesso só depois do pagamento confirmado), mas o caminho feliz da User Story 1 já destrava o time.

**Independent Test**: Enviar a mesma confirmação duas vezes, uma confirmação com credencial errada e uma confirmação de um pedido inexistente, e conferir os Tenants e o registro de eventos.

**Acceptance Scenarios**:

1. **Given** uma confirmação já processada, **When** o gateway a reenvia, **Then** o Core responde como sucesso e não cria um segundo Tenant, Assinatura ou Membro.
2. **Given** uma confirmação com credencial ausente ou errada, **When** chega ao Core, **Then** é rejeitada, nada é criado e a rejeição fica no log, sem expor a credencial.
3. **Given** uma confirmação autêntica de um pedido desconhecido, **When** chega ao Core, **Then** nada é criado e o evento fica registrado com falha, para análise.
4. **Given** um evento autêntico que não é de pagamento confirmado (por exemplo, cobrança criada), **When** chega ao Core, **Then** é registrado como ignorado e nada muda.
5. **Given** uma falha ao criar o Tenant, **When** o gateway recebe a resposta de erro, **Then** o evento fica registrado com falha e, quando o gateway o reenvia, a ativação termina com um único Tenant.
6. **Given** duas confirmações de pedidos diferentes da mesma organização, **When** as duas chegam, **Then** só um Tenant existe ao final, e a segunda fica registrada como recebida para uma organização já ativa.

---

### User Story 4 - O simulador do Asaas só existe no ambiente local (Priority: P3)

O simulador serve só para desenvolver e testar. Fora do ambiente local, a página de pagamento simulada e o envio da confirmação simulada não existem, mesmo se alguém tentar chamá-los.

**Why this priority**: Evita que alguém crie um Tenant sem pagar. A proteção segue o mesmo modelo do seed local da spec 008.

**Independent Test**: Subir o Core com o perfil de produção e conferir que a página de pagamento simulada não responde e que pedir uma contratação não devolve um endereço do simulador.

**Acceptance Scenarios**:

1. **Given** o perfil de produção, **When** alguém acessa a página de pagamento simulada ou a ação "Simular pagamento", **Then** o Core responde como rota inexistente.
2. **Given** o perfil de produção, **When** o Core sobe, **Then** ele exige a credencial de recebimento das confirmações do gateway, como já exige as variáveis da Clerk.

---

### Edge Cases

- O administrador pede a contratação duas vezes antes de pagar: vale o último pedido; o anterior fica cancelado. Se a confirmação do pedido anterior chegar mesmo assim e a organização ainda não tiver Tenant, a ativação acontece com o plano daquele pedido, porque o pagamento foi feito.
- A confirmação chega para uma organização que já tem Tenant (pago duas vezes, ou Tenant criado pelo seed local): nada é alterado, e o evento fica registrado como recebido para organização já ativa, para tratamento manual (reembolso fica fora do escopo).
- A organização é apagada na Clerk antes da confirmação: o Tenant é criado do mesmo jeito; ele fica sem uso, porque nenhum token válido traz essa organização.
- A pessoa que fez o pedido sai da organização antes da confirmação: o Tenant é criado e ela fica como DONO, e a saída é aplicada pelo webhook da Clerk como em qualquer saída de membro.
- O seed local (`DEV_TENANT_CLERK_ORG_ID`) continua funcionando. Uma organização listada lá já tem Tenant e não precisa contratar.
- A pessoa troca de organização no meio da contratação: o pedido pertence à organização ativa no momento do pedido.
- A confirmação traz um valor diferente do preço do plano: o Core usa o plano gravado no pedido, não o valor da confirmação, e registra a diferença no log.
- O plano pedido não existe no catálogo: o pedido é recusado com um código de erro, sem pedido criado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST oferecer a lista dos planos disponíveis para contratação, com nome e preço, a qualquer sessão válida com organização ativa, tenha ela Tenant ou não.
- **FR-002**: O Core MUST permitir que o administrador de uma organização sem Tenant peça a contratação de um plano. O pedido MUST registrar a organização, a pessoa que pediu, o nome da empresa informado e o plano, com status pendente, e devolver o endereço da página de pagamento do gateway.
- **FR-003**: Pedidos de contratação de quem não é administrador MUST ser recusados com a recusa de negócio `not_admin`. Pedidos de uma organização que já tem Tenant MUST ser recusados com o código `already_subscribed`.
- **FR-004**: Uma organização MUST ter no máximo um pedido pendente. Um pedido novo MUST cancelar o pendente anterior.
- **FR-005**: O Core MUST permitir que a organização sem Tenant consulte o estado do pedido mais recente (pendente, pago ou cancelado), para o Web saber quando o acesso foi liberado.
- **FR-006**: Para uma sessão válida com organização ativa e papel conhecido, mas sem Tenant, toda rota fora das de contratação (FR-001, FR-002 e FR-005) MUST responder 403 com o código `subscription_required` e uma mensagem, sem revogar as sessões. As demais recusas de segurança (sem organização, papel desconhecido, Tenant suspenso) e o 401 continuam como hoje.
- **FR-007**: O Core MUST receber as confirmações do gateway em um endpoint próprio e MUST rejeitar as que não trouxerem a credencial configurada para o gateway.
- **FR-008**: Cada evento recebido do gateway MUST ser registrado uma única vez, pelo identificador do evento, com o tipo, o pedido referenciado e o resultado (executado, ignorado ou falha), conforme o EventoGateway da ADR_125. Um evento repetido MUST ser respondido como sucesso sem novo processamento.
- **FR-009**: A confirmação de pagamento de um pedido MUST criar, para a organização do pedido, exatamente um Tenant ativo, uma Assinatura ativa com o plano do pedido e o Membro DONO para quem pediu, e MUST marcar o pedido como pago. Se a organização já tiver Tenant, nada é criado e o evento fica registrado como recebido para organização já ativa.
- **FR-010**: Uma falha durante a ativação MUST deixar o evento registrado com falha e responder erro ao gateway, para que o reenvio do mesmo evento termine a ativação sem duplicar nada. O registro MUST guardar o que a rota interna de reprocessamento da ADR_133 vai precisar; a rota em si fica para feature própria.
- **FR-011**: Eventos autênticos do gateway que não sejam de pagamento confirmado MUST ser registrados como ignorados, sem efeito.
- **FR-012**: O acesso ao gateway MUST passar por uma interface própria do Core (LLT-235), com duas implementações: o simulador local, nesta feature, e o Asaas real, em feature futura. Trocar uma pela outra MUST NOT exigir mudança no recebimento da confirmação nem na ativação.
- **FR-013**: O simulador MUST existir só no ambiente local. Ele MUST oferecer uma página de pagamento com a ação "Simular pagamento", que envia ao endpoint de confirmação do FR-007 um evento no formato do evento de pagamento confirmado do Asaas, com a mesma credencial.
- **FR-014**: Fora do ambiente local, o Core MUST NOT expor o simulador e MUST exigir a credencial de recebimento das confirmações para subir.
- **FR-015**: Pedidos, confirmações, rejeições e ativações MUST ser registrados no log no padrão da constituição (princípio VI), com o código do evento, sem credenciais, valores de cartão ou dados pessoais.
- **FR-016**: Nenhuma credencial do gateway (chave de API ou credencial de confirmação) MUST aparecer em código, teste, documentação versionada ou exemplo commitado (princípio V).

### Key Entities

- **Plano**: opção de contratação com nome e preço, um por nível de `PlanTier` (FREE a ENTERPRISE). Nesta etapa, o catálogo e os preços são configuração do Core, sem tela de gestão.
- **Pedido de assinatura**: intenção de contratar feita por um administrador para uma organização. Guarda a organização, a pessoa, o nome da empresa, o plano, o identificador da cobrança no gateway e o status (pendente, pago, cancelado). É o elo entre o pedido e a confirmação.
- **Evento do gateway (EventoGateway)**: cada notificação recebida do gateway, com identificador único, tipo, pedido referenciado, data, resultado e erro, se houver (ADR_125/133).
- **Tenant**: entidade já existente (ADR_121/122). Passa a nascer também pela confirmação do pagamento, além do seed local.
- **Assinatura**: plano e status do Tenant (ADR_122). Nesta feature nasce ativa, com o plano do pedido. Limites e ciclo de cobrança ficam para features futuras.
- **Membro DONO**: o Membro (ADR_122/123) de quem fez o pedido, com papel DONO, criado na ativação.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um administrador com uma organização nova chega da escolha do plano à tela da Empresa, pelo simulador, em até 2 minutos e sem editar configuração nem reiniciar o Core.
- **SC-002**: Em 100% dos casos, uma confirmação enviada várias vezes resulta em exatamente 1 Tenant, 1 Assinatura e 1 Membro DONO.
- **SC-003**: 0 Tenants são criados por confirmações sem a credencial correta ou por pedidos inexistentes.
- **SC-004**: 0 pessoas de organizações sem Tenant são deslogadas por falta de Tenant; todas recebem o código de contratação pendente.
- **SC-005**: Com o perfil de produção, 0 rotas do simulador respondem.
- **SC-006**: Depois de uma falha na ativação, 100% dos eventos reprocessados terminam com o Tenant ativo e sem duplicação.

## Assumptions

- O gateway escolhido é o Asaas (LLT-89). A integração real (cliente, cobrança, checkout, sandbox e túnel para receber webhooks na máquina local) é feature futura. Esta feature define a interface e o formato da confirmação com base na documentação pública do Asaas, a conferir no research.
- Nesta etapa, o simulador é a única implementação do gateway. Sem ele (fora do ambiente local), pedir uma contratação responde que o pagamento não está disponível, até o adaptador real existir.
- No Asaas real, uma cobrança de valor zero não existe. Como o plano gratuito também passa pela confirmação, a feature do adaptador real precisa definir como confirmar o plano gratuito (por exemplo, validação do cartão). No simulador, o plano gratuito é confirmado como os demais.
- Os preços dos planos ainda não estão fechados. O catálogo usa valores de configuração; a lista de planos com franquias e limites é a LLT-230.
- O token da Clerk não traz o nome da organização. O nome da empresa do Tenant é o informado no pedido.
- A Assinatura do Tenant (ADR_122) é independente da `PlanSubscription` por produto do módulo de uso (LLT-183/185), que esta feature não altera. Como as duas se relacionam é decisão do plano, alinhada com o Michel.
- O seed local da spec 008 continua existindo e não muda.
- A tela de planos, o desvio pelo código `subscription_required` e a espera pela confirmação no Web são uma spec própria do `lhamalog-web`. O comportamento atual do Web (spec 006), que encerra a sessão só no 403 sem código, já mantém a pessoa logada diante do `subscription_required`.
- O webhook da Clerk continua sem criar Tenant (spec 003). Vínculos de membros feitos antes da ativação são tratados como hoje.
- Atraso de pagamento, inadimplência, renovação, troca de plano, cancelamento e reembolso estão fora do escopo (LLT-232, LLT-233 e features futuras).
