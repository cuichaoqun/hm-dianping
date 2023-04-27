package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.LIST_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = LIST_SHOP_KEY  + "head";
        String shops = stringRedisTemplate.opsForValue().get(key);
        if(CharSequenceUtil.isNotBlank(shops)){
            List<ShopType> shopTypes = JSONUtil.toList(new JSONArray(shops), ShopType.class);
            return Result.ok(shopTypes);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if(CollUtil.isEmpty(typeList)){
            return Result.fail("首页列表不存在");
        }
        String shopJson = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(key, shopJson);
        return Result.ok(typeList);
    }

}


