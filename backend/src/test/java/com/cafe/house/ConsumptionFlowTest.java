package com.cafe.house;

import com.cafe.house.common.BizException;
import com.cafe.house.entity.BeanBatch;
import com.cafe.house.entity.MenuItem;
import com.cafe.house.entity.MenuRecipe;
import com.cafe.house.entity.OrderTicket;
import com.cafe.house.entity.TableInfo;
import com.cafe.house.repository.BeanBatchRepository;
import com.cafe.house.repository.MenuItemRepository;
import com.cafe.house.repository.MenuRecipeRepository;
import com.cafe.house.repository.OrderConsumptionRepository;
import com.cafe.house.repository.OrderTicketRepository;
import com.cafe.house.repository.TableInfoRepository;
import com.cafe.house.service.MenuItemService;
import com.cafe.house.service.OrderTicketService;
import com.cafe.house.service.OrderTicketService.AddItemRequest;
import com.cafe.house.service.OrderTicketService.CreateTicketRequest;
import com.cafe.house.service.OrderTicketService.FinishRequest;
import com.cafe.house.service.OrderTicketService.OrderItemInput;
import com.cafe.house.service.OrderTicketService.OrderTicketDetail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配方与咖啡豆消耗闭环的集成测试（H2 MySQL 模式）：
 * 完成时扣减、跨批次拆分、整单全量或全不扣、历史快照、并发不超扣不重复。
 */
@SpringBootTest
class ConsumptionFlowTest {

    @Autowired
    private OrderTicketService ticketService;
    @Autowired
    private MenuItemService menuItemService;
    @Autowired
    private BeanBatchRepository batchRepo;
    @Autowired
    private MenuItemRepository menuRepo;
    @Autowired
    private MenuRecipeRepository recipeRepo;
    @Autowired
    private OrderTicketRepository ticketRepo;
    @Autowired
    private OrderConsumptionRepository consumptionRepo;
    @Autowired
    private TableInfoRepository tableRepo;

    @BeforeEach
    void clean() {
        consumptionRepo.deleteAll();
        ticketRepo.deleteAll();
        recipeRepo.deleteAll();
        menuRepo.deleteAll();
        batchRepo.deleteAll();
        tableRepo.deleteAll();
    }

    // ---------------- 测试数据构造 ----------------

    private BeanBatch batch(String no, String origin, double remainingG, String status, LocalDateTime createdAt) {
        BeanBatch b = new BeanBatch();
        b.batchNo = no;
        b.origin = origin;
        b.roastLevel = "中";
        b.weightKg = BigDecimal.valueOf(remainingG / 1000);
        b.remainingG = BigDecimal.valueOf(remainingG);
        b.status = status;
        b.createdAt = createdAt;
        return batchRepo.save(b);
    }

    private MenuItem menu(String name, double price) {
        MenuItem m = new MenuItem();
        m.name = name;
        m.price = BigDecimal.valueOf(price);
        m.category = "意式";
        m.status = "在售";
        return menuRepo.save(m);
    }

    private void recipe(MenuItem m, String origin, double grams) {
        MenuRecipe r = new MenuRecipe();
        r.menuItemId = m.id;
        r.beanOrigin = origin;
        r.grams = BigDecimal.valueOf(grams);
        recipeRepo.save(r);
    }

    private OrderTicketDetail openTicketWith(MenuItem m, int qty) {
        TableInfo t = new TableInfo();
        t.tableNo = "T" + Long.toString(System.nanoTime(), 36);
        t.seats = 2;
        t.status = "占用";
        tableRepo.save(t);
        return ticketService.create(new CreateTicketRequest(t.id, List.of(new OrderItemInput(m.id, qty))));
    }

    // ---------------- 用例 ----------------

    @Test
    void finishDeductsAndSnapshots() {
        BeanBatch b = batch("BB-1", "巴西", 1000, "已入库", LocalDateTime.now().minusDays(5));
        MenuItem latte = menu("拿铁", 28);
        recipe(latte, "巴西", 18);

        OrderTicketDetail d = openTicketWith(latte, 2);
        OrderTicketDetail finished = ticketService.finish(d.ticket().id, new FinishRequest("小王"));

        assertEquals("已完成", finished.ticket().status);
        assertEquals("小王", finished.ticket().finishedBy);
        assertNotNull(finished.ticket().finishedAt);
        assertEquals(1, finished.consumptions().size());
        assertEquals(0, BigDecimal.valueOf(36).compareTo(finished.consumptions().get(0).grams()));
        assertEquals("BB-1", finished.consumptions().get(0).batchNo());
        assertEquals("小王", finished.consumptions().get(0).operator());
        assertEquals(0, BigDecimal.valueOf(964).compareTo(batchRepo.findById(b.id).get().remainingG));
    }

    @Test
    void crossBatchSplitFifo() {
        BeanBatch older = batch("BB-OLD", "巴西", 20, "已入库", LocalDateTime.now().minusDays(10));
        BeanBatch newer = batch("BB-NEW", "巴西", 100, "已入库", LocalDateTime.now().minusDays(1));
        batch("BB-WAIT", "巴西", 500, "待烘焙", LocalDateTime.now().minusDays(3)); // 未入库不参与
        MenuItem latte = menu("拿铁", 28);
        recipe(latte, "巴西", 18);

        OrderTicketDetail d = openTicketWith(latte, 3); // 需 54g：老批 20 + 新批 34
        OrderTicketDetail finished = ticketService.finish(d.ticket().id, new FinishRequest("小李"));

        assertEquals(2, finished.consumptions().size());
        assertEquals("BB-OLD", finished.consumptions().get(0).batchNo());
        assertEquals(0, BigDecimal.valueOf(20).compareTo(finished.consumptions().get(0).grams()));
        assertEquals("BB-NEW", finished.consumptions().get(1).batchNo());
        assertEquals(0, BigDecimal.valueOf(34).compareTo(finished.consumptions().get(1).grams()));
        // 老批次扣光自动转「已用完」
        assertEquals("已用完", batchRepo.findById(older.id).get().status);
        assertEquals(0, BigDecimal.valueOf(66).compareTo(batchRepo.findById(newer.id).get().remainingG));
    }

    @Test
    void insufficientStockRollsBackEverything() {
        BeanBatch b = batch("BB-1", "巴西", 30, "已入库", LocalDateTime.now());
        MenuItem latte = menu("拿铁", 28);
        recipe(latte, "巴西", 18);

        // 点单时就会提示库存不足
        TableInfo t = new TableInfo();
        t.tableNo = "T-x";
        t.seats = 2;
        t.status = "占用";
        tableRepo.save(t);
        OrderTicketDetail d = ticketService.create(new CreateTicketRequest(t.id, List.of()));
        BizException orderEx = assertThrows(BizException.class,
                () -> ticketService.addItem(d.ticket().id, new AddItemRequest(latte.id, 2)));
        assertTrue(orderEx.getMessage().contains("库存不足"));

        // 改成 1 杯可以点；再把配方调大导致完成时不足 → 整单不扣
        ticketService.addItem(d.ticket().id, new AddItemRequest(latte.id, 1));
        menuItemService.saveRecipe(latte.id, List.of(new MenuItemService.RecipeLineInput("巴西", BigDecimal.valueOf(40))));
        BizException finishEx = assertThrows(BizException.class,
                () -> ticketService.finish(d.ticket().id, new FinishRequest("小王")));
        assertTrue(finishEx.getMessage().contains("库存不足"));

        // 账单保持「制作中」、批次一颗未扣、无消耗流水
        assertEquals("制作中", ticketRepo.findById(d.ticket().id).get().status);
        assertEquals(0, BigDecimal.valueOf(30).compareTo(batchRepo.findById(b.id).get().remainingG));
        assertTrue(consumptionRepo.findByTicketIdOrderById(d.ticket().id).isEmpty());
    }

    @Test
    void historyImmuneToPriceAndRecipeChanges() {
        batch("BB-1", "巴西", 1000, "已入库", LocalDateTime.now());
        MenuItem latte = menu("拿铁", 28);
        recipe(latte, "巴西", 18);

        OrderTicketDetail d = openTicketWith(latte, 1);
        OrderTicketDetail finished = ticketService.finish(d.ticket().id, new FinishRequest("小王"));

        // 管理员之后调价、改配方
        MenuItem changed = new MenuItem();
        changed.price = BigDecimal.valueOf(99);
        menuItemService.update(latte.id, changed);
        menuItemService.saveRecipe(latte.id, List.of(new MenuItemService.RecipeLineInput("巴西", BigDecimal.valueOf(55))));

        // 历史账单金额与消耗都不变
        OrderTicketDetail again = ticketService.detail(finished.ticket().id);
        assertEquals(0, BigDecimal.valueOf(28).compareTo(again.ticket().total));
        assertEquals(0, BigDecimal.valueOf(28).compareTo(again.items().get(0).price()));
        assertEquals(1, again.consumptions().size());
        assertEquals(0, BigDecimal.valueOf(18).compareTo(again.consumptions().get(0).grams()));
    }

    @Test
    void concurrentFinishSameTicketAdvancesOnce() throws Exception {
        batch("BB-1", "巴西", 1000, "已入库", LocalDateTime.now());
        MenuItem latte = menu("拿铁", 28);
        recipe(latte, "巴西", 18);
        OrderTicketDetail d = openTicketWith(latte, 1);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        Runnable attempt = () -> {
            try {
                ticketService.finish(d.ticket().id, new FinishRequest("并发员"));
                ok.incrementAndGet();
            } catch (BizException e) {
                fail.incrementAndGet();
            } catch (Throwable t) {
                errors.add(t);
            }
        };
        runConcurrent(2, attempt, attempt);
        assertTrue(errors.isEmpty(), () -> "并发执行出现非业务异常: " + errors);

        assertEquals(1, ok.get());
        assertEquals(1, fail.get());
        assertEquals("已完成", ticketRepo.findById(d.ticket().id).get().status);
        assertEquals(1, consumptionRepo.findByTicketIdOrderById(d.ticket().id).size());
        assertEquals(0, BigDecimal.valueOf(982).compareTo(batchRepo.findAll().get(0).remainingG));
    }

    @Test
    void concurrentFinishDifferentTicketsNeverOverdraws() throws Exception {
        BeanBatch b = batch("BB-1", "巴西", 30, "已入库", LocalDateTime.now());
        MenuItem latte = menu("拿铁", 28);
        recipe(latte, "巴西", 20);
        OrderTicketDetail d1 = openTicketWith(latte, 1); // 需 20
        OrderTicketDetail d2 = openTicketWith(latte, 1); // 也需 20，合计 40 > 30

        AtomicInteger ok = new AtomicInteger();
        runConcurrent(2, () -> {
            try {
                ticketService.finish(d1.ticket().id, new FinishRequest("甲"));
                ok.incrementAndGet();
            } catch (BizException ignored) {
            }
        }, () -> {
            try {
                ticketService.finish(d2.ticket().id, new FinishRequest("乙"));
                ok.incrementAndGet();
            } catch (BizException ignored) {
            }
        });

        // 只有一张能全量满足；余量绝不能扣成负数
        assertEquals(1, ok.get());
        BigDecimal remaining = batchRepo.findById(b.id).get().remainingG;
        assertEquals(0, BigDecimal.valueOf(10).compareTo(remaining));
        long rows = consumptionRepo.count();
        assertEquals(1, rows);
        // 失败账单仍是「制作中」，可按原流程重试
        long making = ticketRepo.findAll().stream().filter(t -> "制作中".equals(t.status)).count();
        assertEquals(1, making);
    }

    // ---------------- 并发工具 ----------------

    private void runConcurrent(int threads, Runnable... tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.length);
        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        go.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
    }
}
