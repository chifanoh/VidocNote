package com.vidoc;

import com.vidoc.config.AlipayProperties;
import com.vidoc.config.LlmProperties;
import com.vidoc.config.PointsProperties;
import com.vidoc.config.TurnstileProperties;
import com.vidoc.config.YtDlpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({YtDlpProperties.class, LlmProperties.class, PointsProperties.class,
        AlipayProperties.class, TurnstileProperties.class})
public class VidocServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VidocServerApplication.class, args);
        log.info("========== server started ==========");
    }

}
