package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        String s = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, s, time, unit);
    }

    //逻辑过期
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    public <R, ID> R queryWithPassThrouth(
            String keyProfix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyProfix + id;
        // 1.查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在 返回
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            //返回错误信息 解决缓存穿透问题
            return null;
        }
        // 4.不存在查库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 5.不存在 写入空值  返回错误
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6。存在写入缓存返回
        set(key, r, time, unit);
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFollback, Long time, TimeUnit unit) {
        String key = CACHE_SHOP_KEY + id;
        // 1.查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在 返回 null
            R apply = dbFollback.apply(id);
            this.setWithLogicalExpire(key, apply, time, unit);
            return apply;
        }
        // 4.命中 查询过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
//            过期时间未过期 直接返回shop
            return r;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if (lock) {
//            TODO 获取锁成功 ，开启独立线程，进行缓存重建。
            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                重建缓存
                log.info("缓存开始重建 id:{}", id);
                try {
                    R apply = dbFollback.apply(id);
                    this.setWithLogicalExpire(key, apply,time,unit);

                } catch (Exception e) {
                    log.info("添加缓存异常 id:{},异常：{}", id, e);
                } finally {
//                释放锁
                    unLock(lockKey);
                }

            });
        }
        return r;
    }

    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 20, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
