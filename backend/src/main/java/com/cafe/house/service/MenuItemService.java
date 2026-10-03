package com.cafe.house.service;

import com.cafe.house.common.BizException;
import com.cafe.house.common.Dict;
import com.cafe.house.entity.MenuItem;
import com.cafe.house.repository.MenuItemRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MenuItemService {

    @Autowired
    private MenuItemRepository repo;

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
}
