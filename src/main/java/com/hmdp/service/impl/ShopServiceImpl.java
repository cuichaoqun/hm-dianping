package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        //  Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
//        解决缓存穿透 数据库没有缓存没有直接到数据库 缓存 空对象
//        Shop shop = cacheClient.
//                queryWithPassThrouth(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        解决缓存击穿 1.高并发key
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("shop 为空！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在 返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            //返回错误信息
            return null;
        }
        // 开始实现缓存重建
        // 4.4 成功，根据id查询数据库
        // 4.不存在 查库
        String lockKey = "" + id;
        Shop shop = null;
        try {
            //4.1获取互斥锁
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否获取成功
            if (!isLock) {
                //4.3 失败，则休眠并重试
                Thread.sleep(30);
                return queryWithMutex(id);
            }
            // 查询成功
            Thread.sleep(200);
            shop = getById(id);
            if (shop == null) {
                // 5.不存在 写入空值  返回错误
                stringRedisTemplate.opsForValue().set(key, "", 2L, TimeUnit.MINUTES);
                return null;
            }
            // 6。存在写入缓存返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.debug("获取商品信息异常，异常原因：{}", e);
        } finally {
            unLock(lockKey);
        }

        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("id不存在");
        }
        updateById(shop);
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在 返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            //返回错误信息 解决缓存穿透问题
            return null;
        }
        // 4.不存在查库
        Shop shop = getById(id);
        if (shop == null) {
            // 5.不存在 写入空值  返回错误
            stringRedisTemplate.opsForValue().set(key, "", 2L, TimeUnit.MINUTES);
            return null;
        }

        // 6。存在写入缓存返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.不存在 返回 null
            return null;
        }
        // 4.命中 查询过期时间
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop1 = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
//            过期时间未过期 直接返回shop
            return shop1;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if (lock) {
//            TODO 获取锁成功 ，开启独立线程，进行缓存重建。
            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                重建缓存
                log.info("缓存开始重建 id:{}", id);
                try {
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    log.info("添加缓存异常 id:{},异常：{}", id, e);
                } finally {
//                释放锁
                    unLock(lockKey);
                }

            });
        }
        return shop1;
    }

    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 20, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireTime) {
        Shop byId = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(byId);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
