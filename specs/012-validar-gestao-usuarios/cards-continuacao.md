# Cards de continuação (LLT-119)

**Versão final de 25/11/2026 (T038).** Texto pronto para o Pedro criar os cards no Jira. **O card 7 (R-17) é um bug e tem prioridade.** O agente não cria nada no Jira. Os responsáveis são sugestões; quem decide é o time (D2).

Cada card traz o achado de origem, que está no [relatorio-aceite.md](relatorio-aceite.md), e o comentário de revisão, que está no [revisao-prs.md](revisao-prs.md).

---

## 1. ADR: modelo de membro da LLT-163 (D1)

**Responsável sugerido:** Michel (autor das ADRs de Clerk)
**Origem:** D1, R-12

**Descrição.** Em 17/11/2026, o time decidiu que vale o modelo de membro da LLT-163: webhook da Clerk, chave secreta no Core, cargos DONO/ADMIN/MEMBRO e coleção `member`. Ele substitui o da LLT-167/168, em que o Core não chama a Clerk e só conhece `admin`/`member`. A decisão já está no código (a LLT-163 entrou pelo PR #6), mas ainda não tem ADR. As ADRs locais ADR_022 (02/10) e ADR_122 (11/10) descrevem o modelo antigo.

**Critério de aceite:**
- Uma ADR nova registra a decisão, o contexto (LLT-163 contra LLT-167/168) e as consequências: a unificação do card 2 e a migração do card 3.
- A ADR diz quais ADRs anteriores ela substitui.

---

## 2. Unificar `CompanyMember`/`CompanyAdminCount` com a coleção `member`

**Responsável sugerido:** Felipe e Vinicius
**Origem:** R-12 e R-02; revisão #8, #10 e #11

**Descrição.** Duas coleções guardam membros na `Development`: a `CompanyMember`/`CompanyAdminCount` da LLT-168 (status, último acesso e a trava do último admin) e a `member` da LLT-163, sincronizada pelo webhook. A trava do último admin usa o cargo do último token da pessoa (R-02, risco aceito no contrato da 168). Por isso, um admin rebaixado na Clerk que ainda não navegou continua contando como admin.

**Critério de aceite:**
- Uma única fonte de membros por empresa. Status, último acesso e cargo ficam no mesmo modelo.
- A trava do último admin usa o cargo sincronizado pelo webhook, e não o do último token. Isso fecha o R-02.
- Os testes de aceite da LLT-119 (`CompanyMemberAcceptanceTests`) e o `CompanyMemberHttpTests` continuam verdes, ou são adaptados com a mesma intenção.
- Os textos da 167 (FR-004, FR-014) e da 168 (FR-016, "Fronteira") que contradizem a D1 são atualizados.

---

## 3. Migrar ou descartar os `CompanyMember` antigos

**Responsável sugerido:** Felipe
**Origem:** R-12; revisão #11

**Descrição.** Os `CompanyMember` gravados antes da LLT-163 usam o id da organização da Clerk no `tenantId` e no `_id`. Desde a LLT-163, o `tenantId` é o id interno do tenant. Os registros antigos não são mais encontrados e podem inflar o `CompanyAdminCount` se o contador for recriado (R-01, janela b).

**Critério de aceite:**
- Um script ou migração converte os registros antigos para o id interno, ou o time decide descartá-los por escrito.
- O `CompanyAdminCount` de cada empresa bate com a contagem real depois da migração.

---

## 4. Melhorias no pacote `member`: leituras, índice e logs

**Responsável sugerido:** Vinicius
**Origem:** R-04 e R-05; revisão #1 a #3, #5 e #6

**Descrição.**
- **R-04:** o interceptor lê o mesmo `CompanyMember` duas vezes em toda requisição `/api/**` (`CompanyMemberService.java:43` e `:48`). A coleção não tem índice por `tenantId`. A contagem de admins roda a cada `ensureCount()`.
- **R-05:** o `MemberClock` tem setters só para teste, no código de produção, quando o padrão do projeto é receber `InstantSource` (como no `QuotaReservationService`). Desativar, reativar e a recusa `last_admin` não geram log de negócio (princípio VI).

**Critério de aceite:**
- Uma leitura por requisição no interceptor.
- `@CompoundIndex` por `tenantId` (lista) e por `tenantId`/`administrator`/`status` (contagem).
- `CompanyMemberService` recebe `InstantSource` e o intervalo pelo construtor, e o `MemberClock` sai.
- Logs `event=member.deactivation.succeeded`, `event=member.deactivation.last_admin_refused` e `event=member.access.deactivated_refused`, sem dado pessoal. **Depende da D5** (log de negócio ou auditoria da LLT-170).

> Se o card 2 reescrever o pacote, este card vira critério de aceite do card 2.

---

## 5. Web: convite com retorno para o LhamaLog (R-15)

**Responsável sugerido:** Felipe (integração com a Clerk)
**Origem:** R-15 (E2E-05, 25/11)

**Descrição.** O `organization.inviteMember` é chamado sem `redirectUrl` (`TeamPage.tsx:231` e `:287`). O link do e-mail de convite abre o portal da Clerk (`*.accounts.dev`, em inglês), e não a tela de cadastro do LhamaLog. As mensagens em português de convite expirado ou revogado (`invitationTicketErrors`, `src/features/team/invitations.ts:71-74`) nunca aparecem nesse caminho.

**Critério de aceite:**
- O convite e o reenvio passam `redirectUrl` para a `/sign-up` do Web, na origem atual.
- Abrir o link de um convite revogado mostra a mensagem em português do LhamaLog.
- Quem aceita o convite cai na empresa do convite, no Web.

---

## 6. Web: ajustes pequenos na Equipe (R-10, R-11 e R-16)

**Responsável sugerido:** Vinicius
**Origem:** R-10, R-11 e R-16; revisão #16 e #17

**Descrição.**
- **R-10:** a coluna "E-mail" mostra o `publicUserData.identifier`. Com login por telefone ou username, aparece outra coisa (`src/features/team/members.ts:23-24,39`).
- **R-11:** o Reenviar revoga o convite e cria outro. A tela diz "Ele expira em 7 dias", mas não avisa que o link anterior deixa de valer.
- **R-16:** no Desativar, o Web pergunta "Desativar …?" (`TeamPage.tsx:449`) antes de conferir a trava do último admin (`:461-472`). Quem é o último admin confirma e só depois recebe a recusa.
- O Membro que abre a `/equipe` vê "Você não tem acesso. Selecione uma empresa válida." (E2E-16). A empresa é válida; falta dizer que a tela é só para administradores.
- O e-mail vazio no convite mostra "Informe um e-mail válido." (`src/features/team/validation.ts:11`). O documento-base pedia "Informe o e-mail.". Só ajustar se o time quiser os dois textos.

**Critério de aceite:**
- A coluna mostra o e-mail principal quando ele existir, ou passa a se chamar "Identificador".
- A mensagem de sucesso do Reenviar avisa que o link anterior deixa de funcionar.
- O último admin recebe a recusa sem ver a pergunta de confirmação.
- O Membro na `/equipe` vê uma mensagem que diz que a tela é de administradores.

---

## 7. Bug no Web: trocar o cargo na Equipe falha com "Organization role not found" (R-17)

**Responsável sugerido:** Felipe (integração com a Clerk), com aviso ao Vinicius (LLT-168)
**Origem:** R-17 (E2E-10, 25/11). **Prioridade:** alta, porque nenhuma troca de cargo funciona pela tela

**Descrição.** Na `/equipe`, o "Salvar cargo" chama `organization.updateMember` com `role: 'admin'`/`'member'` (`src/features/team/components/TeamPage.tsx:410`; o valor vem do select em `TeamPage.tsx:612-615` e do `roleToUpdate` em `src/features/team/members.ts:27-29`). A instância Clerk só conhece `org:admin`/`org:member`, os mesmos do convite (`src/features/team/types.ts:1`). A Clerk responde 404 "Organization role not found", o texto aparece em inglês na tela e o cargo não muda. Pelo console, `updateMember` com `org:admin` funcionou. Nenhum teste do Web chama o `updateMember`, por isso a suíte (160/160) não pegou.

**Correção sugerida:** mandar `` role: `org:${next}` `` só nessa chamada, sem mexer no `roleToUpdate` nem nas comparações da tela (a trava do último admin e o botão habilitado usam `admin`/`member`).

**Critério de aceite:**
- O admin troca um Membro para Administrador na `/equipe` e vê "Cargo atualizado para Administrador.". O cargo muda na Clerk.
- O caminho contrário (Administrador → Membro) também funciona.
- A trava do último admin continua bloqueando, sem chamar a Clerk.
- O Web continua sem chamar `/api/company/members/{id}/role`.
- Um teste novo confere que o `updateMember` recebe `org:admin`/`org:member`.
- Depois da correção, refazer o E2E-10 da LLT-119 (AC-17).

---

## Não viram card

- **R-01:** descartado. Vale só um comentário no código sobre o invariante do contador (revisão #7).
- **R-03:** descartado como falha. Vale um comentário no código (revisão #4).
- **R-06 e R-07:** processo, tratado pela D4 (padrão `LLT-XXX-slug` da LLT-285).
- **R-08, R-09, R-13 e R-14:** resolvidos ou descartados.
