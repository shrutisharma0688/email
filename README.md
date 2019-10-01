# Email Service REST API

## Description
This application is exposed as RESTful API which acts as an abstraction between two different email service providers.
It supports a quick failover from one provider to the other by doing a simple health check on the providers.  

This application uses MailGun and SendGrid as its providers, please refer to the following for more information on their APIs

* https://sendgrid.com/docs/API_Reference/Web_API_v3/index.html
* https://documentation.mailgun.com/en/latest/user_manual.html#sending-via-api

## Tech stack
* Spring Boot 1.5.x
* Java 1.8 and above
* Maven 3.x  
* Postman(optional)



## Todo
* Build a BadRequestException to handle bad requests effectively
* Send a notification to someone if it failed to connect to both providers
* Save the pending email to the queue/database for future and and re-attempt 
* Unit tests to be fixed
* Scheduler/Job to go through the list of items in the 'pending' table/queue  
* Solution deployment for outside access using URL



## Architecture

### Layers
* Resource - Handles the request and response
* Service - Validates and processes the request and generates a response

### Properties files
* mailgun-mail.properties - MailGun properties
* mailgun-mail-test.properties - MailGun properties for testing
* sendgrid-mail.properties - SendGrid properties
* sendgrid-mail-test.properties - SendGrid properties for testing  



## Process flow
1. Client sends a request to /api/emails
2. EmailResource#sendEmail() accepts the requests and calls EmailService#sendEmail()
3. EmailService#sendEmail() does the following
    * Validates the inputs and will throw Exception if there's an error
    * Executes health check on the primary provider and if it fails it'll try the secondary provider. 
    * Creates a connection to the available email provider
    * Constructs the request body according to the selected provider
    * It returns the response message with timestamp based on whether it's a 'good' or 'bad' response



## Setup

### Mail providers
* Create an account with SendGrid and MailGun
* Take notes on the api url and key
* There are 4 mail properties, 2 are used in local/dev/prod environments where other 2 are used for testing.
    * Update http-api.url and http-api.key on mailgun and sendgrid properties
    * You can leave http-api.from empty because it's not being used at the moment
    

## How to run it from the command line

Once you have finished with the setup and cloning of the repository, you can execute the following command to run it. 
```text
mvn clean package -Dmaven.test.skip=true && java -jar target/email-service-rest-api-0.0.1-SNAPSHOT.jar
```
If you want to run the test you run below command 
```text
mvn clean package && java -jar target/email-service-rest-api-0.0.1-SNAPSHOT.jar
```

Once started you should see the following lines and can start using it
```text
s.b.c.e.t.TomcatEmbeddedServletContainer : Tomcat started on port(s): 8080 (http)
com.mail.EmailApplication                : Started EmailApplication in 5.05 seconds (JVM running for 5.704)
```  



## Endpoints
#### Sending an email
A 'POST' request is used to send an email to one or more recipients. 'to', 'cc' and 'bcc' are optionals but at least one has to be set.

Request structure

* "from" - The sender in String - Mandatory
* "to" - An array of recipients in String - Optionals - Max 10 recipients
* "cc" - An array of recipients in String - Optionals- Max 10 recipients
* "bcc" - An array of recipients in String - Optionals - Max 10 recipients
* "subject" - The email subject - Mandatory
* "text" - The email body - Mandatory

Request example

```text
POST /api/emails HTTP/1.1
Content-Type: application/json;charset=UTF-8
Host: localhost
Content-Length: <xyz>

{
  "from": "whoami@example.org",
  "to": [
    "shruti@example.org"
  ],
  "subject": "Test Email!",
  "text": "Hi!!! This is test email"
}

OR, use below complete request 

http://localhost:8080/api/emails?from=whoami@example.org&to=shruti@example.org&subject=Test Email!&text=Hi!!! This is test email.
```

Response example
```text
HTTP/1.1 201 Created
Content-Type: application/json;charset=UTF-8
Content-Length: <xyz>

{
    "message": "Yayy, Your email has been sent!!",
    "timestamp": 1569900188436
}
```
