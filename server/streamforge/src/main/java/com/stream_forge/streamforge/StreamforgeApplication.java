package com.stream_forge.streamforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableKafka
@EnableCaching
@EnableAsync
public class StreamforgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamforgeApplication.class, args);
	}

}
