package com.mail.api;

import com.mail.api.BaseResourceTest;

import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class EmailResourceTest extends BaseResourceTest {
	
	/**
     * Test for sending email and the server should return response
     *
     * @throws Exception If an exception occurs
     */
	@Test
    public void sendEmail_Success() throws Exception {
        long beforeSendTimestamp = new LocalDateTime().toDate().getTime();

        Map<String, Object> data = new HashMap<>();
        data.put("from", "shruti.pandey0688@gmail.com");
        data.put("to", new String[]{"shruti.pandey0688@gmail.com", "er.sachin.p@gmail.com"});
        data.put("subject", "This is a test subject");
        data.put("text", "This is a test body");

        RequestBuilder req = post("/api/emails")
                .contentType(APPLICATION_JSON_UTF8)
                .content(super.objectMapper.writeValueAsString(data));

        super.mockMvc.perform(req)
                .andExpect(status().isCreated())
                .andExpect(content().contentType(super.APPLICATION_JSON_UTF8))
                .andExpect(
                        jsonPath("$.message").value("Your email has been sent"))
                .andExpect(
                        jsonPath("$.timestamp", greaterThan(beforeSendTimestamp)));
    }

    /**
     * Test with bad email address where the server should return bad request and an error message
     *
     * @throws Exception If an exception occurs
     */
    @Test
    public void sendEmailWithBadEmail_BadRequestException() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("from", "shruti.pandey0688@gmail.com");
        data.put("to", new String[]{"john@example", "er.sachin.p@gmail.com"});
        data.put("subject", "This is a test subject");
        data.put("text", "This is a test body");

        RequestBuilder req = post("/api/emails")
                .contentType(APPLICATION_JSON_UTF8)
                .content(super.objectMapper.writeValueAsString(data));

        super.mockMvc.perform(req).andReturn();
    }

}
