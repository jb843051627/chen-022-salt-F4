package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TSaltSpotRow;

/**
 * 抽检结果报送核收行 Service接口（batch-process 形状：整批提交，无单条增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltSpotRowService {

    /** 按主键回查单行 */
    TSaltSpotRow selectTSaltSpotRowById(Long id);

    /**
     * 整册一次导入（册子进门唯一路径）：册头校验 → 逐行处置 → 返回**成功落库行数**。
     * 同一册码第二遍进门只与库内照面、不增不减不覆（返回 0）；行数压过单批上限返回 -1 且整册不入库；
     * 空批次（或空册码）返回 0。两个数缺任一的行不落地，纸面行次随该对象带回；
     * 落库行的行次由系统按落库次序排上，排定不再挪。
     */
    int submitBatch(String batchNo, List<TSaltSpotRow> rows);

    /** 该批次被退回（已退回）的行明细，缘由写在行上（按行次升序） */
    List<TSaltSpotRow> listErrors(String batchNo);
}
