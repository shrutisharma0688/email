package com.mail.services;

import com.mail.config.MailGunHttpConfiguration;
import com.mail.config.SendGridHttpConfiguration;
import com.mail.dto.EmailResponseDto;
import com.mail.dto.MailMessageDto;
import com.mail.request.MailGunRequest;
import com.mail.request.MailRequest;
import com.mail.request.SendGridRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final int CONNECT_TIMEOUT = 3000;
    private final SendGridHttpConfiguration sendGridHttpConfig;
    private final MailGunHttpConfiguration mailGunHttpConfig;
    private boolean useSecondMailOnFailover = false;
    
    @Autowired
    public EmailServiceImpl(SendGridHttpConfiguration sendGridHttpConfig, MailGunHttpConfiguration mailGunHttpConfig) {
        this.sendGridHttpConfig = sendGridHttpConfig;
        this.mailGunHttpConfig = mailGunHttpConfig;
    }
    
    @Override
    public EmailResponseDto sendEmail(MailMessageDto dto) throws Exception {
        // Validate the mail message
        List<String> errors = validate(dto);
        // If we found at least an error just cancel the request straight away
        if (errors.size() > 0) {
        	logger.error("Bad Request, throwing Exception!!");
        	//TODO : Can create a BadRequestException to be more specific around bad request failures
            throw new RuntimeException();
        }

        // Validate Health check for fail-over
        if (healthCheck(sendGridHttpConfig.getUrl())) {
        	useSecondMailOnFailover = false;
        } else if (healthCheck(mailGunHttpConfig.getUrl())) {
        	useSecondMailOnFailover = true;
        } else {
            String reason = "Can't reach to any mail providers!!";
            logger.warn(reason);

            //Tell the user that their email has been put into the queue
            //TODO: Send a notification to someone if it failed to connect to both providers
            //TODO: Save the pending email to the queue/database for future and and re-attempt 
            return new EmailResponseDto("Your email has not been sent due to the reason : " + reason, new Date().getTime());
        }

        HttpURLConnection conn = connectAndSendData(dto);

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            String message = "The request gets redirected, this is not supported yet so please check and update the url accordingly in the config - redirected url: " + conn.getHeaderField("Location");
            logger.warn(message);

            String redirectUrl = conn.getHeaderField("Location");
            if (!useSecondMailOnFailover) {
                sendGridHttpConfig.setRedirectUrl(redirectUrl);
            } else {
                mailGunHttpConfig.setRedirectUrl(redirectUrl);
            }
            conn = connectAndSendData(dto);

            responseCode = conn.getResponseCode();
        }

        // Handle normal and error stream
        boolean errorStream = false;
        String responseMsg = "Yayy, Your email has been sent!!";
        InputStream is;
        if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
            is = conn.getInputStream();
        } else {
            errorStream = true;
            is = conn.getErrorStream();
        }

        // Parse the input stream
        StringBuilder response = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        if (errorStream) {
            // Tell the user that their email has been put into the queue
        	//TODO: Save the pending email to the queue/database for future and and re-attempt 
            return new EmailResponseDto("Sorry, Your email has not been sent!!", new Date().getTime());
        }

        return new EmailResponseDto(responseMsg, new Date().getTime());
    }

    /**
     * Establishes a http connection and then send the mail data
     *
     * @param dto Mail message from the client
     * @return Http connection
     * @throws Exception If an exception occurs
     */
    private HttpURLConnection connectAndSendData(MailMessageDto dto) throws Exception {
        // Construct the mail data
        byte[] data = buildMailData(dto);
        // Build the connection
        HttpURLConnection conn = buildConn(String.valueOf(data.length));
        // Let's write the request data
        writeToOutputStream(conn, data);

        return conn;
    }

    /**
     * A helper method to write the data to the output stream
     *
     * @param conn Http connection
     * @param data Data
     * @throws IOException If an exception occurs
     */
    private void writeToOutputStream(HttpURLConnection conn, byte[] data) throws IOException {
        OutputStream os = conn.getOutputStream();
        os.write(data);
        os.close();
    }

    /**
     * This method goes through 'to', 'cc' and 'bcc' arrays and make sure there are no duplicates in the list
     *
     * @param errors List of errors
     * @param dto    Mail message from the client
     */
    private void checkDuplicateRecipients(List<String> errors, MailMessageDto dto) {
        Set<String> toSet = new HashSet<>();
        Set<String> ccSet = new HashSet<>();
        Set<String> bccSet = new HashSet<>();

        Set<String> duplicates = new HashSet<>();
        for (String to : dto.getTo()) {
            if (toSet.contains(to)) {
                duplicates.add(to);
            }
            toSet.add(to);
        }

        for (String cc : dto.getCc()) {
            if (toSet.contains(cc) || ccSet.contains(cc)) {
                duplicates.add(cc);
            }
            ccSet.add(cc);
        }

        for (String bcc : dto.getBcc()) {
            if (toSet.contains(bcc) || ccSet.contains(bcc) || bccSet.contains(bcc)) {
                duplicates.add(bcc);
            }
            bccSet.add(bcc);
        }
        if (duplicates.size() > 0) {
            errors.add(String.format("Email address in to, cc and bcc should be unique - %s", StringUtils.join(duplicates, ",")));
        }
    }

    /**
     * This method performs few validations to make sure that some of the mandatory fields exist, emails are in good format and no duplicacy.
     *
     * @param dto Mail message from the client
     * @return List of errors
     */
    private List<String> validate(MailMessageDto dto) {
        List<String> errors = new ArrayList<>();

        // Mandatory check - the from and to email need to exist
        if (dto.getFrom() == null || "".equals(dto.getFrom())) {
            errors.add("From email is missing");
            logger.error("From email is missing in request!!");

            return errors;
        } else if ((dto.getTo().length == 0) && (dto.getCc().length == 0) && (dto.getBcc().length == 0)) {
            errors.add("To email is missing");
            logger.error("Recipient is missing!!");

            return errors;
        }

        // Email address format check
        EmailValidator emailValidator = EmailValidator.getInstance();
        checkEmailFormat(errors, emailValidator, 
        		new String[]{ dto.getFrom()	},
        		"from");

        checkEmailFormat(errors, emailValidator, dto.getTo(), "to");
        checkEmailFormat(errors, emailValidator, dto.getCc(), "cc");
        checkEmailFormat(errors, emailValidator, dto.getBcc(), "bcc");

        // Check duplicate recipients because some providers will reject duplicates
        checkDuplicateRecipients(errors, dto);

        return errors;
    }

    /**
     * A helper method to validate if the email address is in a good format
     *
     * @param errors    List of errors
     * @param validator Email validator
     * @param emails    An array of emails
     * @param type      To | Cc | Bcc
     */
    private void checkEmailFormat(List<String> errors, EmailValidator validator, String[] emails, String type) {
        for (String email : emails) {
            if (!validator.isValid(email)) {
                errors.add(String.format("'%s' email is invalid - %s", type, email));
            }
        }
    }

    /**
     * Constructs the request body for provider based on the 'useSecondMailOnFailover' flag
     *
     * @param dto Mail message from the client
     * @return Data in byte array
     * @throws Exception If an exception occurs
     */
    private byte[] buildMailData(MailMessageDto dto) throws Exception {
        byte[] data;

        MailRequest request;
        if (!useSecondMailOnFailover) {
            request = new SendGridRequest.Builder(dto.getFrom(), dto.getTo(), dto.getSubject(), dto.getText())
                    .cc(dto.getCc())
                    .bcc(dto.getBcc())
                    .type(dto.getType())
                    .build();
        } else {
            request = new MailGunRequest.Builder(dto.getFrom(), dto.getTo(), dto.getSubject(), dto.getText())
                    .cc(dto.getCc())
                    .bcc(dto.getBcc())
                    .type(dto.getType())
                    .build();
        }
        data = request.getData().getBytes();

        return data;
    }

    /**
     * Builds a Http connection by using SendGrid when 'useSecondMailOnFailover' is false and using MailGun when 'useSecondMailOnFailover' is true
     *
     * @param dataLength Length of the content
     * @return Http connection for SendGrid or MailGun
     * @throws Exception When a connection to the server cannot be established
     */
    private HttpURLConnection buildConn(String dataLength) throws IOException {
        HttpURLConnection conn;
        try {
            if (!useSecondMailOnFailover) {
                conn = buildSendGridConn(dataLength);
            } else {
                conn = buildMailGunConn(dataLength);
            }
        } catch (IOException e) {
            logger.error("Could not connect to the mail provider, failed during building connection");
            throw new IOException("Could not connect to the mail provider, failed during building connection");
        }

        return conn;
    }

    /**
     * This method creates a Http connection for SendGrid provider
     *
     * @param dataLength Length of the content
     * @return Http connection for SendGrid
     * @throws IOException When a connection to the server cannot be established
     */
    private HttpURLConnection buildSendGridConn(String dataLength) throws IOException {
        HttpURLConnection conn = null;

        try {
            String urlStr = sendGridHttpConfig.getRedirectUrl() == null ? sendGridHttpConfig.getUrl() : sendGridHttpConfig.getRedirectUrl();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(sendGridHttpConfig.getRequestMethod());

            conn.setRequestProperty("Content-Type", sendGridHttpConfig.getContentType());
            conn.setRequestProperty("Content-Length", dataLength);
            conn.setRequestProperty("Accept", sendGridHttpConfig.getAcceptType());
            conn.setRequestProperty("Authorization", "Bearer " + sendGridHttpConfig.getKey());

            conn.setDoOutput(true);
            conn.setDoInput(true);
        } catch (IOException e) {
            logger.error("Could not connect to the first mail(SendGrid) provider");
        }

        return conn;
    }

    /**
     * This method creates a Http connection for MailGun provider
     *
     * @param dataLength Length of the content
     * @return Http connection for MailGun
     * @throws IOException When a connection to the server cannot be established
     */
    private HttpURLConnection buildMailGunConn(String dataLength) throws IOException {
        HttpURLConnection conn = null;

        try {
            String urlStr = mailGunHttpConfig.getRedirectUrl() == null ? mailGunHttpConfig.getUrl() : mailGunHttpConfig.getRedirectUrl();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();

            // Set the user and password
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication("api", mailGunHttpConfig.getKey().toCharArray());
                }
            });

            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setInstanceFollowRedirects(false);

            conn.setRequestMethod(mailGunHttpConfig.getRequestMethod());
            conn.setRequestProperty("Content-Type", mailGunHttpConfig.getContentType());
            conn.setRequestProperty("Content-Length", dataLength);

            conn.setDoOutput(true);
            conn.setDoInput(true);
        } catch (IOException e) {
            logger.error("Could not connect to the second mail(MailGun) provider");
        }

        return conn;
    }

    /**
     * To check if the server is responding
     *
     * @param targetUrl The server url
     * @return True if the server returns HTTP_OK or False if the server cannot be reached
     * @throws Exception When the server cannot be reached
     */
    private boolean healthCheck(String targetUrl) throws Exception {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("OPTIONS");

            return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            logger.error("Couldn't establish a connection to " + e.getMessage());
            return false;
        }
    }

}
