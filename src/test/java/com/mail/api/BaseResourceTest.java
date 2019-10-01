package com.mail.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mail.EmailApplication;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.Charset;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {EmailApplication.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@WebAppConfiguration
public abstract class BaseResourceTest {
	
	@Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected ObjectMapper objectMapper;

    final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.ALL.getType(),
            MediaType.ALL.getSubtype(),
            Charset.forName("utf8"));

    protected MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {
    	MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

}