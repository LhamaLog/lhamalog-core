# Checklist de qualidade da spec: Cargos e permissões

**Objetivo**: conferir se a spec está completa antes do plan e do implement.
**Criado em**: 17/11/2026.

## Conteúdo

- [x] Cada história tem prioridade, teste independente e cenários Given/When/Then.
- [x] Os requisitos são testáveis e cada um tem resposta e mensagem definidas.
- [x] Os critérios de sucesso são mensuráveis.
- [x] O que está fora do escopo está listado (FR-024).
- [x] As fontes estão citadas: ADRs, casos de uso, diagrama v11 e código mergeado.

## Pendências antes do implement

- [x] C1: como nasce o Dono.
- [x] C2: rotas em inglês.
- [x] C3: listas de gravidade e tipo.
- [x] C4: valores iniciais dos cargos padrão.
- [x] C5: listas embutidas × coleções de ligação.
- [x] C6: mudança no módulo `member` (avisar o Vinicius).
- [x] C7: Membro continua criando produto.

## Consistência com o que já existe

- [x] Não muda rota existente, exceto FR-020 (desativação do Dono).
- [x] Reaproveita a coleção `AcessoProduto` sem mudar o formato.
- [x] Mantém `admin`/`member` vindos do token (LLT-167/168) e não chama a Clerk.
- [x] Mensagens e códigos seguem o padrão da LLT-168 (`code` no `ApiError`).
- [x] Nenhum segredo da Clerk.
