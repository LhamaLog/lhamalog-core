package br.com.lhamalog.core.usage.api;

import java.util.List;

public record UsageFactPageResponse(List<UsageFactRequest> items, String nextCursor) {}
