package ch.bff.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication
@EnableFeignClients
public class BffProducerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffProducerServerApplication.class, args);
    }

}
