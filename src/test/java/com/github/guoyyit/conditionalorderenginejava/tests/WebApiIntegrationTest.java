package com.github.guoyyit.conditionalorderenginejava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "coengine.data-dir=target/test-data/webapi")
@AutoConfigureMockMvc
class WebApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createTriggerActivateFillAndAuditOverHttp() throws Exception {
        String body = """
                {
                  "accountId": "acct-web",
                  "symbol": "ETH",
                  "side": "SELL",
                  "type": "STOP_LOSS",
                  "direction": "BELOW",
                  "triggerPrice": "100",
                  "priceSource": "LAST",
                  "executionType": "MARKET",
                  "quantity": 3,
                  "maxSlippageBps": 50
                }
                """;
        String response = mockMvc.perform(post("/api/conditional-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/market/ticks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"ETH\",\"timestamp\":10,\"last\":\"105\",\"bid\":\"105\",\"ask\":\"105\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conditional-orders/" + id))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/market/ticks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"ETH\",\"timestamp\":20,\"last\":\"98\",\"bid\":\"98\",\"ask\":\"98\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conditional-orders/" + id))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.activatedReferencePrice").value("98"));

        mockMvc.perform(get("/api/history").param("accountId", "acct-web").param("symbol", "ETH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='CONDITIONAL_ACTIVATED' && @.triggerPrice=='100' && @.referencePrice=='98')]").exists())
                .andExpect(jsonPath("$[?(@.type=='ORDER_FILLED' && @.fillPrice=='98')]").exists());
    }

    @Test
    void slippageBreachReturnsDistinguishableRejection() throws Exception {
        String body = """
                {
                  "accountId": "acct-web2",
                  "symbol": "SOL",
                  "side": "BUY",
                  "type": "TAKE_PROFIT",
                  "direction": "ABOVE",
                  "triggerPrice": "100",
                  "priceSource": "LAST",
                  "executionType": "MARKET",
                  "quantity": 1,
                  "maxSlippageBps": 50
                }
                """;
        String response = mockMvc.perform(post("/api/conditional-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/market/ticks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"SOL\",\"timestamp\":10,\"last\":\"100\",\"bid\":\"100\",\"ask\":\"101\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conditional-orders/" + id))
                .andExpect(jsonPath("$.status").value("REJECTED_SLIPPAGE"));
    }

    @Test
    void modifyThenTriggerUsesNewParameters() throws Exception {
        String body = """
                {
                  "accountId": "acct-web3",
                  "symbol": "XRP",
                  "side": "SELL",
                  "type": "STOP_LOSS",
                  "direction": "BELOW",
                  "triggerPrice": "100",
                  "executionType": "MARKET",
                  "quantity": 1
                }
                """;
        String response = mockMvc.perform(post("/api/conditional-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = mapper.readTree(response);
        String id = order.get("id").asText();

        mockMvc.perform(put("/api/conditional-orders/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggerPrice\":\"90\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/market/ticks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"XRP\",\"timestamp\":10,\"last\":\"95\",\"bid\":\"95\",\"ask\":\"95\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conditional-orders/" + id))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/market/ticks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"XRP\",\"timestamp\":20,\"last\":\"89\",\"bid\":\"89\",\"ask\":\"89\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conditional-orders/" + id))
                .andExpect(jsonPath("$.status").value("FILLED"));
    }
}
