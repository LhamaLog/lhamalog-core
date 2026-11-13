# Modelo de dados

Tenant (coleção tenant, da LLT-163): id = tenantId interno (UUID, criado pelo fluxo de compra, ADR_121), clerkOrgId, name e status do módulo tenant; a Empresa grava no mesmo documento só os dados Empresa (legalName, tradeName, document, registrationDate, size), context (sector, mission, targetAudience, differentiators, language), address (street, number, complement, district, city, state, country, postalCode), version para concorrência otimista. Uma empresa por organização, sem cotas no Core.

Produto: UUID id, tenantId, name obrigatório, description, category e productVersion opcionais, status CONFIGURING inicial (sem transições nesta feature), createdAt, context (problemSolved, targetAudience, differentiators, keywords, restrictions), version. Categoria livre por enum incompleto no modelo, sem inventar catálogo fechado.

AcessoProduto: id determinístico tenant/produto/usuário, tenantId, productId, clerkUserId. Representa vínculo ao membro identificado pelo sub dentro da organização. O cadastro grava o vínculo do criador antes de tornar o Produto disponível. Falha na gravação do produto remove o vínculo; consulta sempre exige produto existente e tenant correspondente. Não é implementada gestão de membros ou novos papéis.

Organização ativa e papel vêm do JWT Clerk validado. Papel built-in admin corresponde ao administrador, sem inferir DONO. Não criar mapeamento de usuário por unsafeMetadata. Membros comuns só consultam/alteram produtos liberados. Administrador = papel admin da organização na Clerk (org:admin). Papéis desconhecidos são recusados pela segurança (403). DONO é papel de negócio do Membro (ADR_123), sincronizado pela LLT-164, e ainda não é usado na autorização de Produto.

Empresa e Produto usam @Version para rejeitar atualização concorrente (409). Identificadores e tenantId não são campos graváveis por DTO. Contextos embutidos evitam atualização parcial em múltiplas coleções. Os documentos são novos: não há migração de dados legados identificada. Índice tenantId em Produto, e tenantId/clerkUserId em AcessoProduto.
