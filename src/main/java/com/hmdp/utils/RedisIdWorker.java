package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ID 生成器
 * */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final long COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){

//      时间戳
        long second = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp  = second - BEGIN_TIMESTAMP;
//      生成序列号
//      先获取当前日期，精确到天
        String yyyyMMdd = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//      自增长 序列号
        Long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + "：" + "");

        return timestamp << COUNT_BITS | count;
    }


    public static void main(String[] args) {
        LocalDateTime of = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = of.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
