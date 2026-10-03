package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltSpotBatchMapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltSpotBatch;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.model.custom.SaltSpotReceipt;
import com.fc.v2.model.custom.SaltSpotRowCount;
import com.fc.v2.service.ITSaltSpotRowService;

/**
 * 抽检结果报送核收行 Service业务层处理。
 *
 * 册子进门的路径只有这一条：册头校验 → 逐行处置 → 库内轧回执，一趟走完。
 * 单行没有自己的新增口、改动口；同册码第二遍“只照面、不记账”只在本类成立。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TSaltSpotRowServiceImpl implements ITSaltSpotRowService {

    /** 每一册行数的顶（处里定下，可由 salt.spot.max-rows 覆盖） */
    @Value("${salt.spot.max-rows:500}")
    private int maxRows;

    /**
     * 当季定下的不合格件数限值：本行不合格件数压过它，本行原路退回。
     * 负值=当季未设限，不据此退任何行（salt.spot.max-fail-qty，每季度定一回）。
     */
    @Value("${salt.spot.max-fail-qty:-1}")
    private int maxFailQty;

    @javax.annotation.Resource
    private TSaltSpotRowMapper saltSpotRowMapper;

    @javax.annotation.Resource
    private TSaltSpotBatchMapper saltSpotBatchMapper;

    @javax.annotation.Resource
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltSpotRow selectTSaltSpotRowById(Long id) {
        return this.saltSpotRowMapper.selectById(id);
    }

    /**
     * 整册进门。整册一个事务：册头先落、行随行落、回执从库里回轧；任一意外整册回滚，
     * 绝不落出“册分两趟、数记两遍”的旧账。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaltSpotReceipt submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        return admit(batchNo, SOURCE_COUNTY, rows);
    }

    /**
     * 按页收册重载：同名方法多挂一层。页只负责把行收束成整册，壳内仍走 admit 同一道门，
     * 不在别处另发生第二遍。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaltSpotReceipt submitBatch(String batchNo, String source, List<SaltSpotRowCount> rows) {
        List<TSaltSpotRow> entities = new ArrayList<TSaltSpotRow>();
        if (rows != null) {
            for (SaltSpotRowCount c : rows) {
                if (c == null) {
                    continue;
                }
                TSaltSpotRow r = new TSaltSpotRow();
                // 只接代号；企业名不随这道门落库，更不许拿它对名录
                r.setItemCode(c.getItemCode());
                r.setHoursNum(c.getSampleCount());
                r.setQty(c.getFailCount());
                entities.add(r);
            }
        }
        return admit(batchNo, source, entities);
    }

    /**
     * 唯一的进门通道。
     */
    private SaltSpotReceipt admit(String batchNo, String source, List<TSaltSpotRow> rows) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("册码不许空着进门");
        }
        String no = batchNo.trim();
        int year = Calendar.getInstance().get(Calendar.YEAR);

        // 一、同册码第二遍：只照面，不增一行、不减一行，头一遍的结果谁也覆不了。
        TSaltSpotBatch head = this.saltSpotBatchMapper.selectById(no);
        if (head != null && head.getDelFlag() != null && head.getDelFlag().intValue() == 0) {
            SaltSpotReceipt existing = rollReceipt(head);
            existing.setDuplicate(true);
            return existing;
        }

        List<TSaltSpotRow> book = rows == null ? new ArrayList<TSaltSpotRow>() : rows;

        // 二、册头先数行：压过行顶，整册原样退回，库里一行（连同册头）都不留。
        if (book.size() > this.maxRows) {
            SaltSpotReceipt turned = new SaltSpotReceipt();
            turned.setBatchNo(no);
            turned.setTotalRows(book.size());
            turned.setReceiptMark(SaltSpotReceipt.MARK_TURNED_AWAY);
            return turned;
        }

        // 三、逐行处置（内存里定去向，行次按落库次序在下一步排；此处不动库）。
        List<TSaltSpotRow> toStore = new ArrayList<TSaltSpotRow>();
        int seq = 0;
        for (TSaltSpotRow incoming : book) {
            seq++;
            TSaltSpotRow r = incoming == null ? new TSaltSpotRow() : incoming;
            // 报册自带的主键、行次、去向、册码一律不作数，进门这一步重排
            r.setId(null);
            r.setRowNo(Integer.valueOf(seq));
            r.setBatchNo(no);
            r.setDelFlag(0);
            r.setArchived(TSaltSpotRow.ARCHIVE_CURRENT);
            r.setReportYear(Integer.valueOf(year));

            BigDecimal sample = r.getHoursNum();
            BigDecimal fail = r.getQty();
            // 两个数须同时递来，缺一个都不许替它补：行照落，去向留“未勾”
            if (sample == null || fail == null) {
                r.setStatus(TSaltSpotRow.STATUS_PENDING);
                r.setRemark("送检件数与不合格件数未同时递齐，缺数不补，暂挂未勾");
                toStore.add(r);
                continue;
            }

            // 收下这一头只认企业代号；拿企业名对的、念不准的，一律算没对上 → 原路退回
            TSaltEnt ent = matchEntByCode(r.getItemCode());
            if (ent == null) {
                r.setStatus(TSaltSpotRow.STATUS_REJECTED);
                r.setRemark("企业代号未对上在册定点企业名录，本行退回");
                toStore.add(r);
                continue;
            }

            // 不合格件数压过当季限值：只退本行，缘由写在行上；同册其余各行照旧。
            // 限值未设（负值）时不动这一条。
            if (this.maxFailQty >= 0 && fail.compareTo(BigDecimal.valueOf(this.maxFailQty)) > 0) {
                r.setStatus(TSaltSpotRow.STATUS_REJECTED);
                r.setRemark("本行不合格件数" + fail.toPlainString() + "压过当季限值" + this.maxFailQty + "，本行退回");
                toStore.add(r);
                continue;
            }

            r.setStatus(TSaltSpotRow.STATUS_ACCEPTED);
            r.setRemark(null);
            toStore.add(r);
        }

        // 四、册头先落（册码即主键，并发的第二趟头一遍会在这撞键，按二遍照面处理），行随行落。
        TSaltSpotBatch newHead = new TSaltSpotBatch();
        newHead.setBatchNo(no);
        // 来路只许两个说法：本处自录 / 县汇交，其余写法一律按县汇交落
        newHead.setSource(SOURCE_SELF.equals(source) ? SOURCE_SELF : SOURCE_COUNTY);
        newHead.setReportYear(Integer.valueOf(year));
        newHead.setDelFlag(0);
        try {
            this.saltSpotBatchMapper.insert(newHead);
        } catch (DuplicateKeyException e) {
            TSaltSpotBatch winner = this.saltSpotBatchMapper.selectById(no);
            if (winner == null || winner.getDelFlag() == null || winner.getDelFlag().intValue() != 0) {
                // 极端竞态：占键那趟尚未可见——不记一行账，只给照面回执
                SaltSpotReceipt pending = new SaltSpotReceipt();
                pending.setBatchNo(no);
                pending.setDuplicate(true);
                return pending;
            }
            SaltSpotReceipt existing = rollReceipt(winner);
            existing.setDuplicate(true);
            return existing;
        }

        for (TSaltSpotRow r : toStore) {
            this.saltSpotRowMapper.insert(r);
        }

        // 五、回执以库内为准逐行轧出，回写册头；册头在，回执才在。
        SaltSpotReceipt receipt = rollReceipt(this.saltSpotBatchMapper.selectById(no));
        settleHead(no, receipt);
        return receipt;
    }

    /** 只认代号、只认在册（del_flag=0 且 status=0）；企业名从不参与对上 */
    private TSaltEnt matchEntByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return this.saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("site_no", code.trim())
                .eq("del_flag", 0)
                .eq("status", 0)
                .last("limit 1"));
    }

    /**
     * 以库内为准把一册的账重新轧一遍。行上去向与回执数字出自同一次查询，不存在屏上临时凑数。
     */
    private SaltSpotReceipt rollReceipt(TSaltSpotBatch head) {
        SaltSpotReceipt receipt = new SaltSpotReceipt();
        receipt.setBatchNo(head.getBatchNo());

        List<TSaltSpotRow> rows = this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", head.getBatchNo())
                .orderByAsc("row_no"));

        int total = 0;
        int accepted = 0;
        int rejected = 0;
        for (TSaltSpotRow r : rows) {
            total++;
            Integer st = r.getStatus();
            if (st != null && st.intValue() == TSaltSpotRow.STATUS_ACCEPTED) {
                accepted++;
            } else if (st != null && st.intValue() == TSaltSpotRow.STATUS_REJECTED) {
                rejected++;
                // 没落地的行把纸面上的行次随回执带回去，缘由一并带上
                receipt.getReturnedLines().add(new SaltSpotReceipt.Line(
                        r.getRowNo(), r.getItemCode(), r.getRemark()));
            } else {
                // 去向还空着（未勾）或情形认不出：收下+退回并不齐头数，当场列差行
                receipt.getImbalanceLines().add(new SaltSpotReceipt.Line(
                        r.getRowNo(), r.getItemCode(),
                        st == null ? "行去向空着不落" : "行去向未勾，暂未落地"));
            }
        }
        receipt.setTotalRows(total);
        receipt.setAcceptedRows(accepted);
        receipt.setRejectedRows(rejected);

        // 核讫那一说是给有数的册备的；一行也没有的册，那一栏写“空册”
        if (total == 0) {
            receipt.setReceiptMark(SaltSpotReceipt.MARK_EMPTY);
        } else if (receipt.getImbalanceLines().isEmpty()) {
            receipt.setReceiptMark(SaltSpotReceipt.MARK_CHECKED);
        }
        return receipt;
    }

    /** 落账：三个数与核讫栏回写册头，回执取自这一趟的返回 */
    private void settleHead(String batchNo, SaltSpotReceipt receipt) {
        UpdateWrapper<TSaltSpotBatch> uw = new UpdateWrapper<TSaltSpotBatch>()
                .eq("batch_no", batchNo).eq("del_flag", 0);
        uw.set("total_rows", receipt.getTotalRows());
        uw.set("accepted_rows", receipt.getAcceptedRows());
        uw.set("rejected_rows", receipt.getRejectedRows());
        uw.set("pending_rows", receipt.getImbalanceLines().size());
        uw.set("receipt_mark", receipt.getReceiptMark());
        this.saltSpotBatchMapper.update(null, uw);
    }

    @Override
    public List<TSaltSpotRow> listByBatch(String batchNo) {
        // 现行名单与往期层都翻得着，一条不许少；撤了册的不摆
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public SaltSpotReceipt receipt(String batchNo) {
        TSaltSpotBatch head = this.saltSpotBatchMapper.selectById(batchNo);
        if (head == null || head.getDelFlag() == null || head.getDelFlag().intValue() != 0) {
            // 没有在册册头就没有回执——根治“开库查不到一行，回执却已发出”
            return null;
        }
        return rollReceipt(head);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeBatch(String batchNo) {
        TSaltSpotBatch head = this.saltSpotBatchMapper.selectById(batchNo);
        if (head == null) {
            return 0;
        }
        List<TSaltSpotRow> rows = listByBatch(batchNo);
        int n = 0;
        for (TSaltSpotRow r : rows) {
            n += this.saltSpotRowMapper.deleteById(r.getId());
        }
        // 物理勾销，册码腾出后方可重新进门（唯一键挡着，软撤无法重报）
        this.saltSpotBatchMapper.deleteById(batchNo);
        return n;
    }

    @Override
    public int archiveStalePending(int currentYear) {
        // 到第二个报送年度仍空着不落的未勾行迁往期层；当年新册不适用
        UpdateWrapper<TSaltSpotRow> uw = new UpdateWrapper<TSaltSpotRow>()
                .eq("status", TSaltSpotRow.STATUS_PENDING)
                .eq("archived", TSaltSpotRow.ARCHIVE_CURRENT)
                .eq("del_flag", 0)
                .lt("report_year", currentYear)
                .set("archived", TSaltSpotRow.ARCHIVE_PAST);
        return this.saltSpotRowMapper.update(null, uw);
    }

    /** 当季限值改在这（每季度定一回） */
    public void setMaxFailQty(int maxFailQty) {
        this.maxFailQty = maxFailQty;
    }

    /** 每册行顶改在这（处里定） */
    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }
}
