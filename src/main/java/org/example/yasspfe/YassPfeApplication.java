package org.example.yasspfe;

import org.example.yasspfe.appscenrios.ApplicationProxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class YassPfeApplication {

    public static void main(String[] args) {
        SpringApplication.run(YassPfeApplication.class, args);

    }
}



