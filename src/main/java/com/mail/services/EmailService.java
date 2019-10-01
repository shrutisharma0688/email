package com.mail.services;

import com.mail.dto.EmailResponseDto;
import com.mail.dto.MailMessageDto;

public interface EmailService {

    /**
     * Sends an email to a list of recipients
     *
     * @param message Message to be sent to the email recipients
     * @return Response to be sent back to the caller
     * @throws Exception When exception occurs
     */
    EmailResponseDto sendEmail(MailMessageDto message) throws Exception;

}
