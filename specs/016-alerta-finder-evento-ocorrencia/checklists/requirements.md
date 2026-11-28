# Specification Quality Checklist: Alerta do Finder e aviso de ocorrência recorrente

**Purpose**: Validar completude e qualidade da especificação antes do planejamento
**Created**: 2026-11-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [ ] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [ ] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [ ] No implementation details leak into specification

## Notes

- Validação em 2026-11-26, uma passagem. Itens acima marcados porque o texto fechado da spec é testável. As marcas desta lista são revisão de qualidade do requisito, não entrega de código.
- Rotas, códigos 200/201, audiência `lhamalog-core` e o canal já existente do aviso do #24 permanecem na spec porque são o contrato decidido (R9, decisões de 26/11, spec 015), não escolha de linguagem ou framework. Não há nome de classe, biblioteca nem stack nova.
- Não há marcador `[NEEDS CLARIFICATION]`. Os pontos sem fonte estão em `spec.md`, seção Pendências, com opções, e os requisitos fechados não escolhem por eles:
  - Pendência 1: efeito da rota de alerta depois do aceite durável (notificação, WebSocket, os dois, ou só gravar).
  - Pendência 2: fórmula da gravidade e se o valor entra no aviso do #24, no documento, nos dois, ou em nenhum lugar visível até a fórmula existir.
  - Pendência 3: motivo diferente do já usado, corpo incompleto, ocorrência que já nasce recorrente, e a mesma entrega que reabre e vira recorrente.
- SC-006 deixa explícito que gravidade não tem aceite enquanto a Pendência 2 estiver aberta.
- Próximo passo possível: `/speckit-plan` para o que está fechado, ou fechar as pendências antes, se o efeito do alerta e a gravidade precisarem entrar no plano. `/speckit-clarify` cabe se for para resolver as três pendências.
