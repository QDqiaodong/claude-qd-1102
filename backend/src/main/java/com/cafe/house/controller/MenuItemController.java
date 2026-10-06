package com.cafe.house.controller;

import com.cafe.house.common.ApiResponse;
import com.cafe.house.entity.MenuItem;
import com.cafe.house.entity.MenuRecipe;
import com.cafe.house.service.ConsumptionService;
import com.cafe.house.service.MenuItemService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu-items")
public class MenuItemController {

    @Autowired
    private MenuItemService service;

    @GetMapping
    public ApiResponse<List<MenuItem>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(status));
    }

    @PostMapping
    public ApiResponse<MenuItem> create(@RequestBody MenuItem input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<MenuItem> update(@PathVariable Long id, @RequestBody MenuItem input) {
        return ApiResponse.success(service.update(id, input));
    }

    /** 饮品配方：豆种 + 每杯克数 */
    @GetMapping("/{id}/recipe")
    public ApiResponse<List<MenuRecipe>> recipe(@PathVariable Long id) {
        return ApiResponse.success(service.recipe(id));
    }

    /** 整体替换配方；不影响已完成账单的历史消耗 */
    @PutMapping("/{id}/recipe")
    public ApiResponse<List<MenuRecipe>> saveRecipe(@PathVariable Long id,
                                                    @RequestBody List<MenuItemService.RecipeLineInput> lines) {
        return ApiResponse.success(service.saveRecipe(id, lines));
    }

    /** 全部出现过的豆种（批次产地），配方维护的下拉选项 */
    @GetMapping("/bean-origins")
    public ApiResponse<List<String>> beanOrigins() {
        return ApiResponse.success(service.beanOrigins());
    }

    /** 在售饮品库存可用性（每杯口径），POS 用来置灰缺料饮品 */
    @GetMapping("/availability")
    public ApiResponse<List<ConsumptionService.MenuAvailability>> availability() {
        return ApiResponse.success(service.availability());
    }
}
