package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {


    @Resource
    private ShopServiceImpl  shopService;

    @Resource
    private CacheClient cacheClient;
    @Test
    public void test(){
        shopService.saveShop2Redis(5L,10L);
    }

    @Test
    public void testCash(){

//        shopService.saveShop2Redis(5L,10L);
        Shop byId = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,byId, 10L, TimeUnit.SECONDS);
    }

}
