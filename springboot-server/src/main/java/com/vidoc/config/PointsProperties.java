package com.vidoc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vidoc.points")
public class PointsProperties {

    /** 注册时一次性赠送的永久积分 */
    private int signupGrant = 100;

    /** 每个用户最多保留的流水条数 */
    private int ledgerKeep = 50;
}
