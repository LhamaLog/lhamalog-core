package br.com.lhamalog.core.upload;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/** One file forwarded to the Collector, backed by a {@link Resource} so the body can be streamed. */
public record OutboundUploadFile(String filename, MediaType contentType, Resource resource, long size) {
}
