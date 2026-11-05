package br.com.lhamalog.core.product;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("AcessoProduto")
@CompoundIndex(name = "product_access_member", def = "{'tenantId': 1, 'clerkUserId': 1, 'productId': 1}", unique = true)
public record ProductAccess(@Id String id, String tenantId, String productId, String clerkUserId) {}
