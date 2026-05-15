package com.grabmyseat.inventory.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceClientTest {

    private MockRestServiceServer server;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new AuthServiceClient(restTemplate, "http://auth-service", "internal-key");
    }

    @Test
    void lookupDisplayById_usesDisplayRouteAndReturnsUsername() {
        server.expect(once(), requestTo("http://auth-service/api/auth/internal/users/id/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "internal-key"))
                .andRespond(withSuccess("{\"userId\":7,\"username\":\"staffuser\"}", MediaType.APPLICATION_JSON));

        AuthServiceClient.DisplayUser result = client.lookupDisplayById(7L);

        assertEquals(7L, result.userId());
        assertEquals("staffuser", result.username());
        server.verify();
    }
}
