package br.com.lhamalog.core.company;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("TenantLogo")
public record CompanyLogo(@Id String id, String contentType, String fileName, byte[] content, String dataBase64,
		Instant updatedAt) {}
