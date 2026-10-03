package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.service.ITSaltSpotRowService;

/**
 * 抽检结果报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * 册子进门的路径只有这一条：册头校验 → 逐行处置 → 轧出回执数，一趟走完。
 * 册内单行没有自己的新增口，也没有自己的改动口。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltSpotRowServiceImpl implements ITSaltSpotRowService {

    /** 单册行数顶：头一遍进门先数行，压过这个顶的整册原样退回，库里一行都不留 */
    private static final int MAX_ROWS = 500;
    /** 当季定下的那一个数：本行不合格件数压过它，这一行原路送回 */
    private static final BigDecimal QTY_LIMIT = new BigDecimal("10");

    /** 去向三选一：0未勾 */
    private static final int STATUS_UNCHECKED = 0;
    /** 去向三选一：1已收下 */
    private static final int STATUS_ACCEPT = 1;
    /** 去向三选一：2已退回 */
    private static final int STATUS_RETURN = 2;

    @javax.annotation.Resource
    private TSaltSpotRowMapper saltSpotRowMapper;
    @javax.annotation.Resource
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltSpotRow selectTSaltSpotRowById(Long id) {
        return this.saltSpotRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        // 空册：一行也没收着，回 0，库里不留
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 册码一册一个，进门那一刻定死；空码不成册
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return 0;
        }
        String no = batchNo.trim();
        // 同一个册码第二遍进来只做一件事：与库里已有的行照面——不增一行、不减一行，头一遍落下的结果谁也覆不了
        Integer exist = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (exist != null && exist.intValue() > 0) {
            return 0;
        }
        // 头一遍进门先数行：压过单册行数顶，整册原样退回，库里一行不留
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        int ok = 0;
        Map<String, Boolean> entCache = new HashMap<String, Boolean>();
        for (int i = 0; i < rows.size(); i++) {
            TSaltSpotRow r = rows.get(i);
            if (r == null) {
                continue;
            }
            // 行次不留给人填：由系统按落库次序排上，排定不再挪；行不许游到别册去
            r.setId(null);
            r.setRowNo(Integer.valueOf(i + 1));
            r.setBatchNo(no);
            // 两个数须同时递上来，缺哪一个都不许替它补：这一行不落地，纸面行次随回执带回去
            String lack = lackOf(r);
            if (lack != null) {
                r.setRemark("缺" + lack + "，不许替补，本行未落地");
                continue;
            }
            // 去向只许单值：压过当季定下的数，这一行原路送回，缘由写在行上；同册其余各行照旧
            if (r.getQty().compareTo(QTY_LIMIT) > 0) {
                r.setStatus(Integer.valueOf(STATUS_RETURN));
                r.setRemark("本行不合格件数" + r.getQty().stripTrailingZeros().toPlainString()
                        + "压过当季限定" + QTY_LIMIT.stripTrailingZeros().toPlainString() + "，原路送回");
            } else if (matchEnt(r.getItemCode(), entCache)) {
                // 收下这一头只认企业代号，拿企业名去对的、名字念不准的，一律算没对上
                r.setStatus(Integer.valueOf(STATUS_ACCEPT));
            } else {
                r.setStatus(Integer.valueOf(STATUS_UNCHECKED));
            }
            r.setDelFlag(Integer.valueOf(0));
            ok += this.saltSpotRowMapper.insert(r);
        }
        // 回执三个数以库内为准，一行一行轧出来：本趟落库几行就报几行
        return ok;
    }

    /** 缺项点名：企业代号、送检件数、不合格件数哪一样没递上来；三样齐全返回 null */
    private String lackOf(TSaltSpotRow r) {
        StringBuilder sb = new StringBuilder();
        if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()) {
            sb.append("、企业代号");
        }
        if (r.getHoursNum() == null) {
            sb.append("、送检件数");
        }
        if (r.getQty() == null) {
            sb.append("、不合格件数");
        }
        return sb.length() == 0 ? null : sb.substring(1);
    }

    /** 只按企业代号对名录（在册且未删除才算对上），不拿企业名去对 */
    private boolean matchEnt(String itemCode, Map<String, Boolean> entCache) {
        String code = itemCode.trim();
        Boolean hit = entCache.get(code);
        if (hit != null) {
            return hit.booleanValue();
        }
        List<TSaltEnt> ents = this.saltEntMapper.selectList(new QueryWrapper<TSaltEnt>()
                .eq("site_no", code).eq("del_flag", 0));
        boolean ok = false;
        for (TSaltEnt e : ents) {
            if (e.getStatus() == null || e.getStatus().intValue() != 1) {
                ok = true;
                break;
            }
        }
        entCache.put(code, Boolean.valueOf(ok));
        return ok;
    }

    @Override
    public List<TSaltSpotRow> listErrors(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TSaltSpotRow>();
        }
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo.trim())
                .eq("del_flag", 0)
                .eq("status", STATUS_RETURN)
                .orderByAsc("row_no"));
    }
}
