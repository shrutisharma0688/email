package com.mail.api;

import com.mail.dto.MailMessageDto;
import com.mail.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailResource {

    private final EmailService emailService;

    @Autowired
    public EmailResource(EmailService emailService) {
        this.emailService = emailService;
    }

    @RequestMapping(value = "/api/emails", method = RequestMethod.POST, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> sendEmail(MailMessageDto mailMessage) throws Exception {
    	
    	ResponseEntity<?> re = new ResponseEntity<>(emailService.sendEmail(mailMessage), HttpStatus.CREATED);
    	
        return re;
    }
}
