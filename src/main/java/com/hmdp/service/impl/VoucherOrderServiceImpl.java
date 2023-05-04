package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
//        1.查询优惠券
        SeckillVoucher byId = iSeckillVoucherService.getById(voucherId);
//        2.判断秒杀是否开启
        if(byId.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("未到秒杀开始时间");
        }
//        3.判断秒杀是否已经结束
        if(byId.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
//        4.判断库存是否充足
        if(byId.getStock()<1){
            return Result.fail("库存不足");
        }
//        5.扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock -1 ")
                .eq("voucher_id", voucherId)
                .update();
        if(!success){
            return Result.fail("库存不足");
        }
//        6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long order = redisIdWorker.nextId("order");
        voucherOrder.setId(order);
//        Long id = UserHolder.getUser().getId();
//        voucherOrder.setUserId(id);
//        7.返回订单id
        voucherOrder.setUserId(1L);
        voucherOrder.setVoucherId(byId.getVoucherId());
        save(voucherOrder);
        return Result.ok(byId.getVoucherId());
    }
}
