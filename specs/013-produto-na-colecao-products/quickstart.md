# Quickstart: Produto gravado em `products`

**Spec**: [spec.md](spec.md)

## 1. Migração dos bancos existentes (antes de subir esta versão)

Rode no banco do Core de cada ambiente (local, dev, produção), com o Core parado ou ainda na versão anterior. Troque `<URI do banco do Core>` no terminal; não grave o URI em arquivo.

```powershell
mongosh "<URI do banco do Core>" --quiet --eval @'
const names = db.getCollectionNames();
const hasOld = names.includes("Produto");
const hasNew = names.includes("products");
if (hasOld && hasNew && db.products.countDocuments({}) > 0) {
  print("PARE: existem Produto e products com documentos. Confira a mão antes de seguir.");
  quit(1);
}
if (hasOld) {
  if (hasNew) { db.products.drop(); print("products vazia descartada"); }
  db.Produto.renameCollection("products");
  print("Produto renomeada para products");
}
const r = db.products.updateMany({ productId: { $exists: false } }, [{ $set: { productId: "$_id" } }]);
print("productId preenchido em " + r.modifiedCount + " documento(s)");
'@
```

Rodar duas vezes é seguro: na segunda, nada muda (`0 documento(s)`).

O Coletor precisa ler este mesmo banco: o `MONGODB_URI` dele aponta para o banco do Core, com usuário só de leitura (`read` nas coleções `products` e `product_tokens`).

## 2. Testes automáticos

```powershell
.\mvnw.cmd -B clean verify
```

Precisa do Docker aberto (Testcontainers).

## 3. Resultados

| Data | Comando | Resultado |
|---|---|---|
| 27/11/2026 | linha de base `clean verify` na `411f79c` | 441 testes, 0 falhas, 0 pulados; BUILD SUCCESS (3 min 08 s) |
| 27/11/2026 | teste novo antes do código (T002) | 4 de 4 falharam pelo motivo esperado (produto fora de `products`, índice ausente) |
| 27/11/2026 | `clean verify` com a feature | 445 testes (441 + 4 novos), 0 falhas, 0 pulados; BUILD SUCCESS (2 min 50 s) |
| 27/11/2026 | script de migração no `mongo-teste`, banco descartável `lhamalog_llt013_migracao` | só `Produto`: renomeou e preencheu 2; segunda execução: 0; `products` vazia + `Produto`: descartou, renomeou e preencheu 2; as duas com documentos: parou com exit 1 sem mexer. O índice `product_tenant_normalized_name` veio junto na renomeação. Banco apagado no fim |
| 27/11/2026 | Parte D, ambiente: Core desta branch (`spring-boot:run`, 8080) e Coletor da `main` `627af74` (8090, OTLP 4328/4327), os dois com `MONGODB_URI` no `mongo-teste`/`lhamalog_otel_local`; Redis e OpenObserve do compose do Coletor; Web na `LLT-180` (5173) | health 200 nos dois; `lhamalog_otel_local` com `products` e o índice `product_collector_product_id`, sem `Produto` |
| 27/11/2026 | Parte D, linha 9 (`Bearer llt_errada`) | `401`; sem cabeçalho também `401`; `/v1/metrics` `404` |
| 27/11/2026 | Parte D, linhas 1 a 6 e 8 a 25 no Chrome (resultado linha a linha nos quickstart das specs 013 e 014 do Web) | produto gravado em `products` com `productId` = `_id`; chave gerada no Web aceita pelo Coletor (`200`) e logs no OpenObserve com `productid` e `tenantid`; revogada vira `401`; Collector real em HTTP e gRPC entrega no OpenObserve. **A D1 = B funciona de ponta a ponta.** Linha 7 (Bruno) com o Pedro; linha 15 risco aceito |
