package com.cafe.house.service;

import com.cafe.house.common.BizException;
import com.cafe.house.entity.BeanBatch;
import com.cafe.house.entity.MenuRecipe;
import com.cafe.house.entity.OrderConsumption;
import com.cafe.house.entity.OrderItem;
import com.cafe.house.entity.OrderTicket;
import com.cafe.house.repository.BeanBatchRepository;
import com.cafe.house.repository.MenuRecipeRepository;
import com.cafe.house.repository.OrderConsumptionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 配方与咖啡豆消耗：点单时的库存提示、完成制作时的整单扣减。
 *
 * <p>扣减规则：只从「已入库」且有余量的批次分配，按最早入库顺序自动拆分，
 * 单一批次不足可跨批次；整单必须全量满足，否则一颗豆都不扣（事务回滚）。
 * 并发安全：批次行悲观写锁 + 按豆种字典序固定加锁顺序，余量不会超扣。</p>
 */
@Service
public class ConsumptionService {

    @Autowired
    private MenuRecipeRepository recipeRepo;

    @Autowired
    private BeanBatchRepository batchRepo;

    @Autowired
    private OrderConsumptionRepository consumptionRepo;

    /** 某饮品点 qty 杯时的缺料清单（不加锁，用于点单提示与轻校验） */
    public List<Shortage> shortages(Long menuItemId, int qty) {
        List<Shortage> result = new ArrayList<>();
        for (MenuRecipe r : recipeRepo.findByMenuItemId(menuItemId)) {
            BigDecimal need = r.grams.multiply(BigDecimal.valueOf(qty));
            BigDecimal available = availableOf(r.beanOrigin);
            if (available.compareTo(need) < 0) {
                result.add(new Shortage(r.beanOrigin, need, available));
            }
        }
        return result;
    }

    /** 在售饮品库存可用性（每杯口径），POS 用来置灰缺料饮品 */
    public List<MenuAvailability> availability(List<Long> menuItemIds) {
        List<MenuAvailability> result = new ArrayList<>();
        for (Long id : menuItemIds) {
            List<Shortage> shortages = shortages(id, 1);
            result.add(new MenuAvailability(id, shortages.isEmpty(), shortages));
        }
        return result;
    }

    /**
     * 完成制作：按当时配方为整张账单生成消耗并扣减批次余量。
     * 调用方须处于事务中；任一豆种不足则抛 BizException，整单回滚。
     */
    public List<OrderConsumption> consumeForTicket(OrderTicket ticket, List<OrderItem> items, String operator) {
        // 1. 汇总本单各饮品明细的配方需求：origin -> 该豆种整单需求克数
        List<Long> menuItemIds = items.stream().map(it -> it.menuItemId).distinct().toList();
        Map<Long, List<MenuRecipe>> recipeByMenu = new LinkedHashMap<>();
        for (MenuRecipe r : recipeRepo.findByMenuItemIdIn(menuItemIds)) {
            recipeByMenu.computeIfAbsent(r.menuItemId, k -> new ArrayList<>()).add(r);
        }
        // TreeMap：按豆种字典序处理，保证并发事务以相同顺序锁批次行，避免死锁
        Map<String, BigDecimal> needByOrigin = new TreeMap<>();
        for (OrderItem it : items) {
            for (MenuRecipe r : recipeByMenu.getOrDefault(it.menuItemId, List.of())) {
                BigDecimal need = r.grams.multiply(BigDecimal.valueOf(it.qty));
                needByOrigin.merge(r.beanOrigin, need, BigDecimal::add);
            }
        }

        // 2. 逐豆种锁定可分配批次并做整单全量预检；不足直接失败，一颗不扣
        Map<String, List<BeanBatch>> batchesByOrigin = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : needByOrigin.entrySet()) {
            List<BeanBatch> batches = batchRepo.findAllocatableForUpdate(e.getKey());
            BigDecimal available = batches.stream()
                    .map(b -> b.remainingG).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(e.getValue()) < 0) {
                throw new BizException("豆种「" + e.getKey() + "」库存不足：本单共需 "
                        + e.getValue().stripTrailingZeros().toPlainString() + " 克，当前可用 "
                        + available.stripTrailingZeros().toPlainString()
                        + " 克。账单保持「制作中」，未扣减任何批次");
            }
            batchesByOrigin.put(e.getKey(), batches);
        }

        // 3. 全量满足，逐明细按最早入库顺序拆分扣减（可跨批次）
        List<OrderConsumption> consumptions = new ArrayList<>();
        Map<Long, BeanBatch> dirty = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (OrderItem it : items) {
            for (MenuRecipe r : recipeByMenu.getOrDefault(it.menuItemId, List.of())) {
                BigDecimal need = r.grams.multiply(BigDecimal.valueOf(it.qty));
                for (BeanBatch b : batchesByOrigin.get(r.beanOrigin)) {
                    if (need.signum() == 0) {
                        break;
                    }
                    if (b.remainingG.signum() == 0) {
                        continue;
                    }
                    BigDecimal take = b.remainingG.min(need);
                    b.remainingG = b.remainingG.subtract(take);
                    need = need.subtract(take);
                    if (b.remainingG.signum() == 0) {
                        b.status = "已用完";
                    }
                    dirty.put(b.id, b);

                    OrderConsumption c = new OrderConsumption();
                    c.ticketId = ticket.id;
                    c.orderItemId = it.id;
                    c.menuItemId = it.menuItemId;
                    c.menuName = it.menuName;
                    c.batchId = b.id;
                    c.batchNo = b.batchNo;
                    c.beanOrigin = b.origin;
                    c.grams = take;
                    c.operator = operator;
                    c.createdAt = now;
                    consumptions.add(consumptionRepo.save(c));
                }
            }
        }
        batchRepo.saveAll(dirty.values());
        return consumptions;
    }

    /** 批次追溯：该批次被哪些账单消耗过 */
    public List<OrderConsumption> consumptionsOfBatch(Long batchId) {
        return consumptionRepo.findByBatchIdOrderById(batchId);
    }

    private BigDecimal availableOf(String origin) {
        BigDecimal sum = batchRepo.sumAvailableByOrigin(origin);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    // ---------------- 响应记录 ----------------

    public record Shortage(String beanOrigin, BigDecimal needG, BigDecimal availableG) {
    }

    public record MenuAvailability(Long menuItemId, boolean ok, List<Shortage> shortages) {
    }
}
