package com.cafe.house.controller;

import com.cafe.house.common.ApiResponse;
import com.cafe.house.entity.MenuItem;
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
}
