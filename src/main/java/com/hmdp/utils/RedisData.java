package com.hmdp.utils;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RedisData implements Serializable {
//逻辑过期时间
    private LocalDateTime expireTime;
    private Object data;
}
