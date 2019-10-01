package com.mail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmailApplication {

    /**
     * Main method to run a standalone application server
     *
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(EmailApplication.class, args);
    }

}
