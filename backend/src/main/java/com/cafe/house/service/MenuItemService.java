package com.cafe.house.service;

import com.cafe.house.common.BizException;
import com.cafe.house.common.Dict;
import com.cafe.house.entity.MenuItem;
import com.cafe.house.entity.MenuRecipe;
import com.cafe.house.repository.BeanBatchRepository;
import com.cafe.house.repository.MenuItemRepository;
import com.cafe.house.repository.MenuRecipeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MenuItemService {

    @Autowired
    private MenuItemRepository repo;

    @Autowired
    private MenuRecipeRepository recipeRepo;

    @Autowired
    private BeanBatchRepository batchRepo;

    @Autowired
    private ConsumptionService consumptionService;

    public List<MenuItem> list(String status) {
        return status == null ? repo.findAll() : repo.findByStatus(status);
    }

    public MenuItem create(MenuItem input) {
        if (input.name == null || input.name.isBlank()) {
            throw new BizException("饮品名不能为空");
        }
        if (repo.existsByName(input.name)) {
            throw new BizException("饮品名已存在");
        }
        if (input.price == null || input.price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("价格必须大于 0");
        }
        if (!Dict.isValidMenuCategory(input.category)) {
            throw new BizException("类别必须是 意式 / 手冲 / 非咖啡 之一");
        }
        MenuItem m = new MenuItem();
        m.name = input.name;
        m.price = input.price;
        m.category = input.category;
        m.status = input.status == null ? "在售" : input.status;
        if (!Dict.isValidMenuStatus(m.status)) {
            throw new BizException("状态必须是 在售 / 下架 之一");
        }
        return repo.save(m);
    }

    public MenuItem update(Long id, MenuItem input) {
        MenuItem m = repo.findById(id).orElseThrow(() -> new BizException("饮品不存在"));
        if (input.name != null) {
            if (!input.name.equals(m.name) && repo.existsByName(input.name)) {
                throw new BizException("饮品名已存在");
            }
            m.name = input.name;
        }
        if (input.price != null) {
            if (input.price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("价格必须大于 0");
            }
            m.price = input.price;
        }
        if (input.category != null) {
            if (!Dict.isValidMenuCategory(input.category)) {
                throw new BizException("类别必须是 意式 / 手冲 / 非咖啡 之一");
            }
            m.category = input.category;
        }
        if (input.status != null) {
            if (!Dict.isValidMenuStatus(input.status)) {
                throw new BizException("状态必须是 在售 / 下架 之一");
            }
            m.status = input.status;
        }
        return repo.save(m);
    }

    // ---------------- 配方维护 ----------------

    /** 查看饮品配方（豆种 + 每杯克数） */
    public List<MenuRecipe> recipe(Long menuItemId) {
        if (!repo.existsById(menuItemId)) {
            throw new BizException("饮品不存在");
        }
        return recipeRepo.findByMenuItemId(menuItemId);
    }

    /**
     * 整体替换饮品配方。只影响保存之后完成的账单；
     * 已完成账单的消耗以 order_consumption 快照为准，不会被重算。
     */
    @Transactional
    public List<MenuRecipe> saveRecipe(Long menuItemId, List<RecipeLineInput> lines) {
        MenuItem m = repo.findById(menuItemId).orElseThrow(() -> new BizException("饮品不存在"));
        List<RecipeLineInput> safe = lines == null ? List.of() : lines;
        Set<String> seen = new HashSet<>();
        for (RecipeLineInput line : safe) {
            if (line.beanOrigin() == null || line.beanOrigin().isBlank()) {
                throw new BizException("配方中的豆种不能为空");
            }
            if (line.grams() == null || line.grams().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("豆种「" + line.beanOrigin() + "」的每杯用量必须大于 0");
            }
            if (!seen.add(line.beanOrigin().trim())) {
                throw new BizException("豆种「" + line.beanOrigin() + "」在配方中重复");
            }
        }
        recipeRepo.deleteByMenuItemId(m.id);
        recipeRepo.flush();
        for (RecipeLineInput line : safe) {
            MenuRecipe r = new MenuRecipe();
            r.menuItemId = m.id;
            r.beanOrigin = line.beanOrigin().trim();
            r.grams = line.grams();
            recipeRepo.save(r);
        }
        return recipeRepo.findByMenuItemId(m.id);
    }

    /** 全部出现过的豆种（批次产地），配方维护的下拉选项 */
    public List<String> beanOrigins() {
        return batchRepo.findDistinctOrigins();
    }

    /** 在售饮品库存可用性（每杯口径），POS 用来置灰缺料饮品 */
    public List<ConsumptionService.MenuAvailability> availability() {
        List<Long> ids = repo.findByStatus("在售").stream().map(m -> m.id).toList();
        return consumptionService.availability(ids);
    }

    public record RecipeLineInput(String beanOrigin, BigDecimal grams) {
    }
}
