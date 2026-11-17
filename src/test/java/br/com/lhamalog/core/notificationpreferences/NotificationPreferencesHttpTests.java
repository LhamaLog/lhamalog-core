package br.com.lhamalog.core.notificationpreferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.mongodb.MongoDBContainer;
import br.com.lhamalog.core.product.*;
import br.com.lhamalog.core.shared.CompanySession;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "management.otlp.metrics.export.enabled=false", "management.tracing.export.otlp.enabled=false",
    "lhamalog.notification-preferences.catalog.types[test-event]=Evento de teste",
    "lhamalog.notification-preferences.catalog.severities[test-level]=Nível de teste"
})
@AutoConfigureMockMvc
@Import(NotificationPreferencesHttpTests.Database.class)
class NotificationPreferencesHttpTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection
        MongoDBContainer preferencesMongoContainer() { return new MongoDBContainer("mongo:8.0.34"); }
    }
    @Autowired MockMvc mvc;
    @Autowired NotificationPreferencesService service;
    @Autowired ProductRepository products;
    @Autowired ProductAccessRepository accesses;
    @Autowired br.com.lhamalog.core.member.CompanyMemberRepository members;
    private static final String PATH = "/api/user/notification-preferences";
    private static final String ALL = "{\"all\":true,\"ids\":[]}";
    private static String body(boolean web, boolean email, boolean push, String products, String types, String severity, Long version) {
        return """
            {"web":%s,"email":%s,"push":%s,"products":%s,"types":%s,"severities":%s,"version":%s,
             "userId":"forged-user","tenantId":"forged-company"}
            """.formatted(web, email, push, products, types, severity, version);
    }
    private static String selected(String id) { return "{\"all\":false,\"ids\":[\"" + id + "\"]}"; }
    private static RequestPostProcessor session(String company, String user) {
        return jwt().jwt(token -> token.subject(user).claim("o", Map.of("id", company, "rol", "member")));
    }
    @Test void defaultsPersistenceIsolationAndConflicts() throws Exception {
        String company = UUID.randomUUID().toString(), user = UUID.randomUUID().toString();
        var auth = session(company, user);
        mvc.perform(get(PATH).with(auth)).andExpect(status().isOk())
            .andExpect(jsonPath("$.web").value(true)).andExpect(jsonPath("$.email").value(false))
            .andExpect(jsonPath("$.products.all").value(true));
        var saved = mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON)
            .content(body(true,true,false,ALL,ALL,ALL,null))).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(true)).andExpect(jsonPath("$.push").value(false))
            .andExpect(jsonPath("$.userId").doesNotExist()).andReturn();
        long version = JsonMapper.shared().readTree(saved.getResponse().getContentAsString()).get("version").longValue();
        mvc.perform(get(PATH).with(auth)).andExpect(jsonPath("$.email").value(true));
        mvc.perform(get(PATH).with(session(company,"other"))).andExpect(jsonPath("$.email").value(false));
        mvc.perform(get(PATH).with(session("other",user))).andExpect(jsonPath("$.email").value(false));
        mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON)
            .content(body(true,false,true,ALL,ALL,ALL,version))).andExpect(status().isOk())
            .andExpect(jsonPath("$.push").value(true)).andExpect(jsonPath("$.email").value(false));
        mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON)
            .content(body(true,true,false,ALL,ALL,ALL,version))).andExpect(status().isConflict());
        mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON)
            .content(body(true,true,false,ALL,ALL,ALL,null))).andExpect(status().isConflict());
    }
    @Test void rejectsInvalidChannelsCatalogSelectionsAndUnauthenticatedRequests() throws Exception {
        var auth = session(UUID.randomUUID().toString(),UUID.randomUUID().toString());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/catalog").with(auth)).andExpect(status().isOk())
            .andExpect(jsonPath("$.types[0].id").value("test-event"));
        for (String invalid : new String[] {
            body(false,false,false,ALL,ALL,ALL,null),
            body(true,false,false,selected("inaccessible"),ALL,ALL,null),
            body(true,false,false,ALL,selected("undefined-type"),ALL,null),
            body(true,false,false,ALL,ALL,selected("undefined-severity"),null),
            body(true,false,false,"{\"all\":false,\"ids\":[]}",ALL,ALL,null),
            body(true,false,false,ALL,"{\"all\":true,\"ids\":[\"test-event\"]}",ALL,null),
            body(true,false,false,ALL,"{\"all\":false,\"ids\":[\"test-event\",\"test-event\"]}",ALL,null),
            body(true,false,false,ALL,ALL,ALL,null).replace("\"email\":false","\"email\":\"false\""),
            "[]", "{"
        }) mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(auth)).andExpect(jsonPath("$.email").value(false));
    }
    @Test void evaluationCombinesFiltersAndRechecksAccess() throws Exception {
        String company = UUID.randomUUID().toString(), user = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        Product product = products.insert(new Product(id,company,id,id,"","","","ACTIVE",Instant.now(),null,null,null));
        products.insert(new Product(second,company,second,second,"","","","ACTIVE",Instant.now(),null,null,null));
        accesses.insert(new ProductAccess(id + user,company,id,user));
        accesses.insert(new ProductAccess(second + user,company,second,user));
        var auth = session(company,user);
        mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON)
            .content(body(true,true,false,selected(id),selected("test-event"),selected("test-level"),null)))
            .andExpect(status().isOk());
        var recipient = new CompanySession(company,user,false);
        assertThat(service.allows(recipient,"WEB",id,"test-event","test-level")).isTrue();
        assertThat(service.allows(recipient,"EMAIL",id,"test-event","test-level")).isTrue();
        assertThat(service.allows(recipient,"PUSH",id,"test-event","test-level")).isFalse();
        assertThat(service.allows(recipient,"WEB",second,"test-event","test-level")).isFalse();
        assertThat(service.allows(recipient,"WEB",id,"other","test-level")).isFalse();
        assertThat(service.allows(recipient,"WEB",id,"test-event","other")).isFalse();
        assertThat(service.allows(new CompanySession("other",user,true),"WEB",id,"test-event","test-level")).isFalse();
        accesses.deleteById(id + user);
        assertThat(service.allows(recipient,"WEB",id,"test-event","test-level")).isFalse();
        products.save(new Product(product.id(),company,id,id,"","","","ACTIVE",product.createdAt(),Instant.now(),null,product.version()));
        assertThat(service.allows(new CompanySession(company,user,true),"WEB",id,"test-event","test-level")).isFalse();
    }
    @Test void emptyCatalogHasNoInventedValuesAndOnlyAllowsAllForSaving() {
        var empty = new NotificationCatalog(null,null);
        assertThat(empty.view().types()).isEmpty();
        assertThat(empty.view().severities()).isEmpty();
        var isolated = new NotificationPreferencesService(
            org.mockito.Mockito.mock(NotificationPreferencesRepository.class), empty,
            org.mockito.Mockito.mock(ProductService.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> isolated.save(new CompanySession("org","user",false),
            JsonMapper.shared().readTree(body(true,false,false,ALL,selected("invented"),ALL,null))))
            .isInstanceOf(br.com.lhamalog.core.shared.FieldValidationException.class);
    }

    @Test void deactivatedMemberCannotReadCatalogOrChangePreferences() throws Exception {
        String company = UUID.randomUUID().toString(), user = UUID.randomUUID().toString();
        members.insert(new br.com.lhamalog.core.member.CompanyMember(
            br.com.lhamalog.core.member.CompanyMember.idOf(company,user), company,user,false,"deactivated",null,null));
        var auth = session(company,user);
        mvc.perform(get(PATH).with(auth)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("user_deactivated"));
        mvc.perform(get(PATH + "/catalog").with(auth)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).with(auth).contentType(MediaType.APPLICATION_JSON)
            .content(body(true,true,false,ALL,ALL,ALL,null))).andExpect(status().isForbidden());
    }

    @Test void defaultEvaluationIncludesAccessibleProductsButNotOptionalChannels() {
        String company = UUID.randomUUID().toString(), user = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        products.insert(new Product(id,company,id,id,"","","","ACTIVE",Instant.now(),null,null,null));
        accesses.insert(new ProductAccess(id + user,company,id,user));
        var recipient = new CompanySession(company,user,false);
        assertThat(service.allows(recipient,"WEB",id,"test-event","test-level")).isTrue();
        assertThat(service.allows(recipient,"EMAIL",id,"test-event","test-level")).isFalse();
        assertThat(service.allows(recipient,"PUSH",id,"test-event","test-level")).isFalse();
        assertThat(service.allows(recipient,"UNKNOWN",id,"test-event","test-level")).isFalse();
    }
}
