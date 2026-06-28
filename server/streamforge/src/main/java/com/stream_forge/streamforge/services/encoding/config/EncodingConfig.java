package com.stream_forge.streamforge.services.encoding.config;


import com.stream_forge.streamforge.services.encoding.util.JobContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncodingConfig {

    @Bean
    public JobContextFactory jobContextFactory(@Value("${encoding.temp-dir}") String tempDir){
        return new JobContextFactory(tempDir);
    }
}