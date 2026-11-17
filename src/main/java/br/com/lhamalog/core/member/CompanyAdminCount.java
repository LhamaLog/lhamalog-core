package br.com.lhamalog.core.member;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("CompanyAdminCount")
public record CompanyAdminCount(@Id String id, long activeAdminCount) {}
