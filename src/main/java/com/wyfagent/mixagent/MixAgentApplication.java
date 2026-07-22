package com.wyfagent.mixagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MixAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MixAgentApplication.class, args);
    }

}
