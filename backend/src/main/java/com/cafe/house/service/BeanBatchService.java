package com.cafe.house.service;

import com.cafe.house.common.BizException;
import com.cafe.house.common.Dict;
import com.cafe.house.entity.BeanBatch;
import com.cafe.house.repository.BeanBatchRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BeanBatchService {

    @Autowired
    private BeanBatchRepository repo;

    public List<BeanBatch> list(String status) {
        return status == null ? repo.findAll() : repo.findByStatus(status);
    }

    public BeanBatch create(BeanBatch input) {
        if (input.batchNo == null || input.batchNo.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        if (repo.existsByBatchNo(input.batchNo)) {
            throw new BizException("批次号已存在");
        }
        if (input.origin == null || input.origin.isBlank()) {
            throw new BizException("产地不能为空");
        }
        if (!Dict.isValidRoastLevel(input.roastLevel)) {
            throw new BizException("烘焙度必须是 浅 / 中 / 深 之一");
        }
        if (input.weightKg == null || input.weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("重量必须大于 0");
        }
        BeanBatch b = new BeanBatch();
        b.batchNo = input.batchNo;
        b.origin = input.origin;
        b.roastLevel = input.roastLevel;
        b.weightKg = input.weightKg;
        b.status = "待烘焙";
        return repo.save(b);
    }

    public BeanBatch update(Long id, BeanBatch input) {
        BeanBatch b = repo.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        if (input.batchNo != null) {
            if (!input.batchNo.equals(b.batchNo) && repo.existsByBatchNo(input.batchNo)) {
                throw new BizException("批次号已存在");
            }
            b.batchNo = input.batchNo;
        }
        if (input.origin != null) {
            b.origin = input.origin;
        }
        if (input.roastLevel != null) {
            if (!Dict.isValidRoastLevel(input.roastLevel)) {
                throw new BizException("烘焙度必须是 浅 / 中 / 深 之一");
            }
            b.roastLevel = input.roastLevel;
        }
        if (input.weightKg != null) {
            if (input.weightKg.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("重量必须大于 0");
            }
            b.weightKg = input.weightKg;
        }
        return repo.save(b);
    }

    public BeanBatch roast(Long id) {
        BeanBatch b = repo.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        if (!Dict.canTransferBatch(b.status, "烘焙中")) {
            throw new BizException("只能将「待烘焙」的批次送入烘焙");
        }
        b.status = "烘焙中";
        return repo.save(b);
    }

    public BeanBatch finishRoast(Long id) {
        BeanBatch b = repo.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        if (!Dict.canTransferBatch(b.status, "已入库")) {
            throw new BizException("只能将「烘焙中」的批次标记为已入库");
        }
        b.status = "已入库";
        return repo.save(b);
    }

    public BeanBatch deplete(Long id) {
        BeanBatch b = repo.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        if (!Dict.canTransferBatch(b.status, "已用完")) {
            throw new BizException("只有「已入库」的批次才能标记已用完");
        }
        b.status = "已用完";
        return repo.save(b);
    }
}
