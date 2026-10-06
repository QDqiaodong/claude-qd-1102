package com.cafe.house.service;

import com.cafe.house.common.BizException;
import com.cafe.house.common.Dict;
import com.cafe.house.entity.MenuItem;
import com.cafe.house.entity.OrderConsumption;
import com.cafe.house.entity.OrderItem;
import com.cafe.house.entity.OrderTicket;
import com.cafe.house.entity.TableInfo;
import com.cafe.house.repository.MenuItemRepository;
import com.cafe.house.repository.OrderConsumptionRepository;
import com.cafe.house.repository.OrderItemRepository;
import com.cafe.house.repository.OrderTicketRepository;
import com.cafe.house.repository.TableInfoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderTicketService {

    @Autowired
    private OrderTicketRepository ticketRepo;

    @Autowired
    private OrderItemRepository itemRepo;

    @Autowired
    private MenuItemRepository menuRepo;

    @Autowired
    private TableInfoRepository tableRepo;

    @Autowired
    private OrderConsumptionRepository consumptionRepo;

    @Autowired
    private ConsumptionService consumptionService;

    /** 同一桌台允许的未结账状态集合 */
    private static final List<String> ACTIVE_STATUSES = List.of("制作中", "已完成");

    public List<OrderTicket> list(String status) {
        return status == null ? ticketRepo.findAll() : ticketRepo.findByStatus(status);
    }

    /** 开台点单：校验桌台已开台、无重复未结账账单，按加单时刻价格快照汇总 total。 */
    @Transactional
    public OrderTicketDetail create(CreateTicketRequest req) {
        if (req.tableId() == null) {
            throw new BizException("请选择桌台");
        }
        TableInfo table = tableRepo.findById(req.tableId())
                .orElseThrow(() -> new BizException("桌台不存在"));
        if (!"占用".equals(table.status)) {
            throw new BizException("桌台尚未开台，无法点单");
        }
        List<OrderTicket> active = ticketRepo.findByTableIdAndStatusIn(req.tableId(), ACTIVE_STATUSES);
        if (!active.isEmpty()) {
            throw new BizException("该桌台已有未结账账单");
        }
        OrderTicket ticket = new OrderTicket();
        ticket.tableId = req.tableId();
        ticket.status = "制作中";
        ticket.total = BigDecimal.ZERO;
        ticket = ticketRepo.save(ticket);

        List<OrderItemInput> items = req.items() == null ? List.of() : req.items();
        for (OrderItemInput in : items) {
            addItemInternal(ticket, in);
        }
        ticket.total = computeTotal(ticket.id);
        ticketRepo.save(ticket);
        return detail(ticket.id);
    }

    /** 加单：只有「制作中」账单可加；校验饮品在售、数量>0、豆库存足够，重算 total。 */
    @Transactional
    public OrderTicketDetail addItem(Long id, AddItemRequest req) {
        OrderTicket ticket = ticketRepo.findById(id).orElseThrow(() -> new BizException("账单不存在"));
        if (!"制作中".equals(ticket.status)) {
            throw new BizException("只有「制作中」的账单可以加单");
        }
        addItemInternal(ticket, new OrderItemInput(req.menuItemId(), req.qty()));
        ticket.total = computeTotal(id);
        ticketRepo.save(ticket);
        return detail(id);
    }

    /**
     * 标记完成：制作中 → 已完成。
     * 在同一事务里按当时配方生成消耗流水并扣减批次余量；任一豆种不足则整单回滚，
     * 账单保持「制作中」、桌台状态不变，可补足库存后重试。
     */
    @Transactional
    public OrderTicketDetail finish(Long id, FinishRequest req) {
        String operator = req == null ? null : req.operator();
        if (operator == null || operator.isBlank()) {
            throw new BizException("请填写操作者（收银员）");
        }
        // 悲观锁锁定账单行：两名收银员并发完成同一账单时，状态只推进一次
        OrderTicket ticket = ticketRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("账单不存在"));
        if (!Dict.canTransferTicket(ticket.status, "已完成")) {
            throw new BizException("只有「制作中」的账单可以标记完成");
        }
        List<OrderItem> items = itemRepo.findByTicketId(id);
        if (items.isEmpty()) {
            throw new BizException("账单没有明细，无法完成制作");
        }
        consumptionService.consumeForTicket(ticket, items, operator.trim());
        ticket.status = "已完成";
        ticket.finishedBy = operator.trim();
        ticket.finishedAt = LocalDateTime.now();
        ticketRepo.save(ticket);
        return detail(id);
    }

    /** 结账：已完成 → 已结账，并释放桌台为「空闲」。 */
    @Transactional
    public OrderTicketDetail settle(Long id) {
        OrderTicket ticket = ticketRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("账单不存在"));
        if (!Dict.canTransferTicket(ticket.status, "已结账")) {
            throw new BizException("请先完成制作再结账");
        }
        ticket.status = "已结账";
        ticketRepo.save(ticket);
        if (ticket.tableId != null) {
            tableRepo.findById(ticket.tableId).ifPresent(t -> {
                t.status = "空闲";
                tableRepo.save(t);
            });
        }
        return detail(id);
    }

    public OrderTicketDetail detail(Long id) {
        OrderTicket ticket = ticketRepo.findById(id).orElseThrow(() -> new BizException("账单不存在"));
        List<OrderItem> items = itemRepo.findByTicketId(id);
        List<OrderItemDetail> details = new ArrayList<>();
        for (OrderItem it : items) {
            // 一律用加单时刻的快照：饮品改名、调价都不影响历史账单
            BigDecimal subtotal = it.unitPrice.multiply(BigDecimal.valueOf(it.qty));
            details.add(new OrderItemDetail(it.id, it.menuItemId, it.menuName, it.unitPrice, it.qty, subtotal));
        }
        List<ConsumptionDetail> consumptions = new ArrayList<>();
        for (OrderConsumption c : consumptionRepo.findByTicketIdOrderById(id)) {
            consumptions.add(new ConsumptionDetail(
                    c.id, c.orderItemId, c.menuName, c.batchId, c.batchNo,
                    c.beanOrigin, c.grams, c.operator, c.createdAt));
        }
        return new OrderTicketDetail(ticket, details, consumptions);
    }

    private void addItemInternal(OrderTicket ticket, OrderItemInput in) {
        if (in.menuItemId() == null) {
            throw new BizException("请选择饮品");
        }
        if (in.qty() == null || in.qty() <= 0) {
            throw new BizException("数量必须大于 0");
        }
        MenuItem mi = menuRepo.findById(in.menuItemId())
                .orElseThrow(() -> new BizException("饮品不存在"));
        if (!"在售".equals(mi.status)) {
            throw new BizException("饮品「" + mi.name + "」已下架，无法下单");
        }
        // 点单时给出明确的库存不足提示（按配方核对当前可用余量）
        List<ConsumptionService.Shortage> shortages = consumptionService.shortages(mi.id, in.qty());
        if (!shortages.isEmpty()) {
            ConsumptionService.Shortage s = shortages.get(0);
            throw new BizException("饮品「" + mi.name + "」库存不足：豆种「" + s.beanOrigin() + "」需要 "
                    + s.needG().stripTrailingZeros().toPlainString() + " 克，当前可用 "
                    + s.availableG().stripTrailingZeros().toPlainString() + " 克");
        }
        OrderItem oi = new OrderItem();
        oi.ticketId = ticket.id;
        oi.menuItemId = in.menuItemId();
        oi.menuName = mi.name;
        oi.unitPrice = mi.price;
        oi.qty = in.qty();
        itemRepo.save(oi);
    }

    private BigDecimal computeTotal(Long ticketId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem it : itemRepo.findByTicketId(ticketId)) {
            sum = sum.add(it.unitPrice.multiply(BigDecimal.valueOf(it.qty)));
        }
        return sum;
    }

    // ---------------- 请求 / 响应记录 ----------------

    public record OrderItemInput(Long menuItemId, Integer qty) {
    }

    public record CreateTicketRequest(Long tableId, List<OrderItemInput> items) {
    }

    public record AddItemRequest(Long menuItemId, Integer qty) {
    }

    public record FinishRequest(String operator) {
    }

    public record OrderItemDetail(
            Long id, Long menuItemId, String menuName,
            BigDecimal price, Integer qty, BigDecimal subtotal) {
    }

    public record ConsumptionDetail(
            Long id, Long orderItemId, String menuName, Long batchId, String batchNo,
            String beanOrigin, BigDecimal grams, String operator, LocalDateTime createdAt) {
    }

    public record OrderTicketDetail(
            OrderTicket ticket, List<OrderItemDetail> items, List<ConsumptionDetail> consumptions) {
    }
}
