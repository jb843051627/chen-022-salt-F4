package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
 * 抽检册进门规矩的逐条验证。库用 Mockito 内存假库顶住，规矩全部在服务层这一处验。
 *
 * @author fuce
 * @date 2026-10-03
 */
class TSaltSpotRowServiceImplTest {

    private TSaltSpotRowServiceImpl service;
    private TSaltSpotRowMapper rowMapper;
    private TSaltSpotBatchMapper batchMapper;
    private TSaltEntMapper entMapper;

    /** batch_no -> 册头 */
    private final Map<String, TSaltSpotBatch> heads = new TreeMap<String, TSaltSpotBatch>();
    /** 行自增主键 -> 行 */
    private final Map<Long, TSaltSpotRow> rows = new TreeMap<Long, TSaltSpotRow>();
    /** 名录代号 -> 企业 */
    private final Map<String, TSaltEnt> ents = new LinkedHashMap<String, TSaltEnt>();

    private long idSeq = 1000L;

    // 从 QueryWrapper 的片段里抠出 列 运算符 值
    private static final Pattern PRED = Pattern.compile(
            "([a-z_]+)\\s*(=|<|>|<=|>=|<>)\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");
    private static final Pattern SET = Pattern.compile(
            "([a-z_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

    private static Map<String, Object> pairs(Wrapper<?> w) {
        @SuppressWarnings("rawtypes")
        AbstractWrapper aw = (AbstractWrapper) w;
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) aw.getParamNameValuePairs();
        return p;
    }

    /** 抠 wrapper 里的等值条件（column -> value） */
    private static Map<String, Object> eqValues(Wrapper<?> w) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Map<String, Object> p = pairs(w);
        Matcher m = PRED.matcher(w.getSqlSegment().replaceAll("\\s+", " "));
        while (m.find()) {
            if ("=".equals(m.group(2))) {
                out.put(m.group(1), p.get(m.group(3)));
            }
        }
        return out;
    }

    @BeforeEach
    void setUp() {
        service = new TSaltSpotRowServiceImpl();
        ReflectionTestUtils.setField(service, "maxRows", 500);
        ReflectionTestUtils.setField(service, "maxFailQty", 5);

        rowMapper = mock(TSaltSpotRowMapper.class);
        batchMapper = mock(TSaltSpotBatchMapper.class);
        entMapper = mock(TSaltEntMapper.class);
        ReflectionTestUtils.setField(service, "saltSpotRowMapper", rowMapper);
        ReflectionTestUtils.setField(service, "saltSpotBatchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "saltEntMapper", entMapper);

        heads.clear();
        rows.clear();
        ents.clear();
        seedEnt("JY00", "盐泽制盐一厂（制盐企业，在册）", 0);
        seedEnt("JY01", "青卤批发部（转行后摘牌）", 1);

        // ---- 册头假库 ----
        when(batchMapper.selectById(any())).thenAnswer(inv -> heads.get(String.valueOf((Object) inv.getArgument(0))));
        when(batchMapper.insert(any(TSaltSpotBatch.class))).thenAnswer(inv -> {
            TSaltSpotBatch h = inv.getArgument(0);
            if (heads.containsKey(h.getBatchNo())) {
                throw new DuplicateKeyException("uk batch");
            }
            heads.put(h.getBatchNo(), h);
            return 1;
        });
        // 回执落账：解析 UpdateWrapper 的 set 段回写册头
        when(batchMapper.update(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TSaltSpotBatch> uw = inv.getArgument(1);
            Map<String, Object> eq = eqValues(uw);
            TSaltSpotBatch h = heads.get(String.valueOf(eq.get("batch_no")));
            if (h == null) {
                return 0;
            }
            Map<String, Object> p = pairs(uw);
            Matcher m = SET.matcher(uw.getSqlSet());
            while (m.find()) {
                Object v = p.get(m.group(2));
                switch (m.group(1)) {
                    case "total_rows": h.setTotalRows((Integer) v); break;
                    case "accepted_rows": h.setAcceptedRows((Integer) v); break;
                    case "rejected_rows": h.setRejectedRows((Integer) v); break;
                    case "pending_rows": h.setPendingRows((Integer) v); break;
                    case "receipt_mark": h.setReceiptMark((String) v); break;
                    default: break;
                }
            }
            return 1;
        });
        when(batchMapper.deleteById(any())).thenAnswer(inv -> {
            return heads.remove(String.valueOf((Object) inv.getArgument(0))) == null ? 0 : 1;
        });

        // ---- 行假库 ----
        when(rowMapper.insert(any(TSaltSpotRow.class))).thenAnswer(inv -> {
            TSaltSpotRow r = inv.getArgument(0);
            long id = ++idSeq;
            r.setId(id);
            rows.put(id, r);
            return 1;
        });
        when(rowMapper.selectList(any())).thenAnswer(inv -> {
            Map<String, Object> eq = eqValues(inv.getArgument(0));
            List<TSaltSpotRow> out = new ArrayList<TSaltSpotRow>();
            for (TSaltSpotRow r : rows.values()) {
                if (eq.containsKey("batch_no") && !String.valueOf(eq.get("batch_no")).equals(r.getBatchNo())) {
                    continue;
                }
                out.add(r);
            }
            out.sort((a, b) -> a.getRowNo().compareTo(b.getRowNo()));
            return out;
        });
        when(rowMapper.deleteById(any(Serializable.class))).thenAnswer(inv -> {
            Long id = Long.valueOf(String.valueOf((Object) inv.getArgument(0)));
            return rows.remove(id) == null ? 0 : 1;
        });
        // 跨年归档：status=0 archived=0 del_flag=0 且 report_year < 当年
        when(rowMapper.update(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TSaltSpotRow> uw = inv.getArgument(1);
            String seg = uw.getSqlSegment().replaceAll("\\s+", " ");
            Map<String, Object> p = pairs(uw);
            int year = 0;
            Matcher lm = Pattern.compile("report_year < #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}").matcher(seg);
            if (lm.find()) {
                year = ((Number) p.get(lm.group(1))).intValue();
            }
            int n = 0;
            for (TSaltSpotRow r : rows.values()) {
                if (r.getStatus() == TSaltSpotRow.STATUS_PENDING
                        && r.getArchived() == TSaltSpotRow.ARCHIVE_CURRENT
                        && r.getDelFlag() == 0
                        && r.getReportYear() < year) {
                    r.setArchived(TSaltSpotRow.ARCHIVE_PAST);
                    n++;
                }
            }
            return n;
        });
        when(rowMapper.selectById(any())).thenAnswer(inv -> rows.get(Long.valueOf(String.valueOf((Object) inv.getArgument(0)))));

        // ---- 名录假库：只认代号 ----
        when(entMapper.selectOne(any())).thenAnswer(inv -> {
            Map<String, Object> eq = eqValues(inv.getArgument(0));
            String code = String.valueOf(eq.get("site_no"));
            TSaltEnt e = ents.get(code);
            // 查询自带 status=0（在册）与 del_flag=0 条件，摘牌/删除的按没对上处理
            if (e != null && e.getStatus() != null && e.getStatus().intValue() == 1) {
                return null;
            }
            return e;
        });
    }

    private void seedEnt(String code, String name, int status) {
        TSaltEnt e = new TSaltEnt();
        e.setId((long) code.hashCode());
        e.setSiteNo(code);
        e.setSiteName(name);
        e.setStatus(status);
        e.setDelFlag(0);
        ents.put(code, e);
    }

    private TSaltSpotRow row(String code, Integer sample, Integer fail) {
        TSaltSpotRow r = new TSaltSpotRow();
        r.setItemCode(code);
        r.setHoursNum(sample == null ? null : BigDecimal.valueOf(sample));
        r.setQty(fail == null ? null : BigDecimal.valueOf(fail));
        return r;
    }

    // 1）正常一册：代号对名录→收；代号对不上→退；不合格压过当季限值→退本行
    @Test
    void testNormalBookAcceptedAndRejected() {
        List<TSaltSpotRow> book = new ArrayList<TSaltSpotRow>();
        book.add(row("JY00", 10, 2));       // 收下
        book.add(row("NO-SUCH", 5, 0));    // 代号没对上，退
        book.add(row("JY00", 8, 9));       // 不合格 9 压过限值 5，退本行

        SaltSpotReceipt rc = service.submitBatch("B01", book);

        assertEquals(3, rc.getTotalRows());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(2, rc.getRejectedRows());
        assertTrue(rc.getImbalanceLines().isEmpty(), "收+退=总，账平：" + rc.getImbalanceLines());
        assertEquals(SaltSpotReceipt.MARK_CHECKED, rc.getReceiptMark());
        assertEquals(2, rc.getReturnedLines().size());
        // 三数以库内为准回写了册头
        TSaltSpotBatch h = heads.get("B01");
        assertEquals(Integer.valueOf(3), h.getTotalRows());
        assertEquals(Integer.valueOf(1), h.getAcceptedRows());
        assertEquals(Integer.valueOf(2), h.getRejectedRows());
        assertEquals("核讫", h.getReceiptMark());
    }

    // 2）两个数须同时递来，缺一个不替它补：行照落，去向留未勾，账不平不核讫
    @Test
    void testMissingOneCountStaysPending() {
        List<TSaltSpotRow> book = new ArrayList<TSaltSpotRow>();
        book.add(row("JY00", 10, null));   // 缺不合格件数
        book.add(row("JY00", null, 1));    // 缺送检件数
        book.add(row("JY00", 4, 1));       // 齐，收下

        SaltSpotReceipt rc = service.submitBatch("B02", book);

        assertEquals(3, rc.getTotalRows());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(0, rc.getRejectedRows());
        assertEquals(2, rc.getImbalanceLines().size(), "缺数两行列差行");
        assertNull(rc.getReceiptMark(), "账不平不能核讫");
        assertEquals(2, rows.values().stream().mapToInt(r -> r.getStatus() == TSaltSpotRow.STATUS_PENDING ? 1 : 0).sum());
    }

    // 3）册头先数行，压过行顶整册原样退回，库里一行（连册头）都不留
    @Test
    void testOverCapWholeBookTurnedAwayNothingStored() {
        ReflectionTestUtils.setField(service, "maxRows", 2);
        List<TSaltSpotRow> book = new ArrayList<TSaltSpotRow>();
        book.add(row("JY00", 1, 0));
        book.add(row("JY00", 1, 0));
        book.add(row("JY00", 1, 0));

        SaltSpotReceipt rc = service.submitBatch("B03", book);

        assertEquals(SaltSpotReceipt.MARK_TURNED_AWAY, rc.getReceiptMark());
        assertEquals(3, rc.getTotalRows());
        assertTrue(heads.isEmpty());
        assertTrue(rows.isEmpty());
    }

    // 4）一行也没有的册，核讫栏写“空册”，不发核讫
    @Test
    void testEmptyBookMarkedEmpty() {
        SaltSpotReceipt rc = service.submitBatch("B04", new ArrayList<TSaltSpotRow>());
        assertEquals(0, rc.getTotalRows());
        assertEquals(SaltSpotReceipt.MARK_EMPTY, rc.getReceiptMark());
        assertTrue(heads.containsKey("B04"));
        assertEquals("空册", heads.get("B04").getReceiptMark());
    }

    // 5）同一册码第二遍只照面：不增不减不覆，回执标注重报；改头一遍落的结果不可能
    @Test
    void testDuplicateBatchOnlyConfers() {
        service.submitBatch("B05", java.util.Arrays.asList(row("JY00", 10, 2), row("NO-SUCH", 3, 0)));
        int rowsAfterFirst = rows.size();

        // 第二遍内容再“不一样”也不记账
        SaltSpotReceipt second = service.submitBatch("B05",
                java.util.Arrays.asList(row("JY00", 99, 98), row("JY00", 77, 66), row("XX", 1, 1)));

        assertTrue(second.isDuplicate());
        assertEquals(rowsAfterFirst, rows.size(), "不增一行");
        assertEquals(2, second.getTotalRows());
        assertEquals(1, second.getAcceptedRows());
        assertEquals(1, second.getRejectedRows());
        assertEquals(Integer.valueOf(2), heads.get("B05").getTotalRows(), "头一遍落账谁也覆不了");
    }

    // 6）行次进门按落库序排定不挪；被退回的行次日再翻仍是原行次，与纸面对得上
    @Test
    void testRowNoAssignedOnEntryAndNeverMoves() {
        List<TSaltSpotRow> book = new ArrayList<TSaltSpotRow>();
        TSaltSpotRow r1 = row("JY00", 10, 2);
        r1.setRowNo(999); // 报册自带的行次不作数
        book.add(r1);
        book.add(row("NO-SUCH", 5, 0)); // 退
        book.add(row("JY00", 8, 1));
        service.submitBatch("B06", book);

        List<TSaltSpotRow> stored = service.listByBatch("B06");
        assertEquals(java.util.Arrays.asList(1, 2, 3),
                java.util.Arrays.asList(stored.get(0).getRowNo(), stored.get(1).getRowNo(), stored.get(2).getRowNo()));
        assertEquals(Integer.valueOf(2), stored.get(1).getRowNo(), "退回行行次不腾挪");
        assertEquals(TSaltSpotRow.STATUS_REJECTED, stored.get(1).getStatus());
        // “次日再翻”：重查一遍，行次照旧
        List<TSaltSpotRow> nextDay = service.listByBatch("B06");
        assertEquals(2, nextDay.get(1).getRowNo().intValue());
    }

    // 7）同一企业一季两行，各行其是：一行收不带另一行，一行退不牵扯另一行
    @Test
    void testTwoRowsSameEnterpriseActIndependently() {
        SaltSpotReceipt rc = service.submitBatch("B07",
                java.util.Arrays.asList(row("JY00", 10, 1), row("JY00", 10, 9)));
        List<TSaltSpotRow> stored = service.listByBatch("B07");
        assertEquals(TSaltSpotRow.STATUS_ACCEPTED, stored.get(0).getStatus());
        assertEquals(TSaltSpotRow.STATUS_REJECTED, stored.get(1).getStatus());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(1, rc.getRejectedRows());
    }

    // 8）只认企业代号：拿企业名去对、代号是已摘牌企业的，一律算没对上
    @Test
    void testMatchByCodeOnly() {
        SaltSpotReceipt rc = service.submitBatch("B08", java.util.Arrays.asList(
                row("盐泽制盐一厂（制盐企业，在册）", 10, 0), // 拿企业名对
                row("JY01", 10, 0)));                       // 已摘牌
        assertEquals(2, rc.getRejectedRows());
        assertEquals(0, rc.getAcceptedRows());
        for (SaltSpotReceipt.Line l : rc.getReturnedLines()) {
            assertNotNull(l.getReason());
        }
    }

    // 9）撤册后册码方许重新进门；不撤就永远只照面
    @Test
    void testRevokeThenResubmitAllowed() {
        service.submitBatch("B09", java.util.Collections.singletonList(row("JY00", 1, 0)));
        int removed = service.revokeBatch("B09");
        assertEquals(1, removed);
        assertTrue(heads.isEmpty());
        assertTrue(rows.isEmpty());

        SaltSpotReceipt again = service.submitBatch("B09",
                java.util.Arrays.asList(row("JY00", 2, 0), row("JY00", 3, 0)));
        assertFalse(again.isDuplicate());
        assertEquals(2, again.getTotalRows());
    }

    // 10）跨年：到第二个报送年度还空着的未勾行迁往期层；已收已退的不动，当年新册不适用
    @Test
    void testArchiveStalePendingOnly() {
        service.submitBatch("B10", java.util.Arrays.asList(
                row("JY00", null, 1),  // 未勾
                row("JY00", 2, 9),     // 退回
                row("JY00", 2, 0)));   // 收下
        int archived = service.archiveStalePending(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1);
        assertEquals(1, archived);
        List<TSaltSpotRow> stored = service.listByBatch("B10"); // 往期层照样翻得着、一条不少
        assertEquals(3, stored.size());
        assertEquals(TSaltSpotRow.ARCHIVE_PAST, stored.get(0).getArchived().intValue());
        assertEquals(TSaltSpotRow.ARCHIVE_CURRENT, stored.get(1).getArchived().intValue());
        assertEquals(TSaltSpotRow.ARCHIVE_CURRENT, stored.get(2).getArchived().intValue());

        // 当年新册不适用：再归档也不动往期行
        assertEquals(0, service.archiveStalePending(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1));
    }

    // 11）按页收册重载：壳内仍是同一道门；企业名不参与对上；回执就是这一趟的返回
    @Test
    void testPageOverloadGoesThroughSameDoor() {
        SaltSpotRowCount good = new SaltSpotRowCount();
        good.setItemCode("JY00");
        good.setItemName("随便写的名字，不参与对上");
        good.setSampleCount(BigDecimal.TEN);
        good.setFailCount(BigDecimal.ONE);

        SaltSpotRowCount byName = new SaltSpotRowCount();
        byName.setItemCode("盐泽制盐一厂（制盐企业，在册）");
        byName.setItemName("盐泽制盐一厂（制盐企业，在册）");
        byName.setSampleCount(BigDecimal.TEN);
        byName.setFailCount(BigDecimal.ZERO);

        SaltSpotReceipt rc = service.submitBatch("B11", ITSaltSpotRowService.SOURCE_SELF,
                java.util.Arrays.asList(good, byName));
        assertEquals(2, rc.getTotalRows());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(1, rc.getRejectedRows());
        assertEquals(ITSaltSpotRowService.SOURCE_SELF, heads.get("B11").getSource());
    }

    // 12）复核回执以库内为准；无在册册头绝不发回执（旧账：开库查不到一行，回执已发出）
    @Test
    void testReceiptRecomputedFromDbAndNoHeadNoReceipt() {
        service.submitBatch("B12", java.util.Arrays.asList(row("JY00", 10, 2), row("NO-SUCH", 1, 0)));
        SaltSpotReceipt rc = service.receipt("B12");
        assertEquals(2, rc.getTotalRows());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(1, rc.getRejectedRows());
        assertNull(service.receipt("NEVER-EXIST"));
    }

    // 13）册码空着不许进门
    @Test
    void testBlankBatchNoRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitBatch("  ", java.util.Collections.singletonList(row("JY00", 1, 0))));
    }

    // 14）并发第二趟头一遍在册头撞键：输家按“第二遍照面”走，不留下两行
    @Test
    void testRaceOnHeadLosesAndConfers() {
        doAnswer(inv -> {
            TSaltSpotBatch h = inv.getArgument(0);
            heads.put(h.getBatchNo(), h); // 先占上
            throw new DuplicateKeyException("race"); // 唯一键不答应
        }).doAnswer(inv -> {
            TSaltSpotBatch h = inv.getArgument(0);
            heads.put(h.getBatchNo(), h);
            return 1;
        }).when(batchMapper).insert(any(TSaltSpotBatch.class));

        // 库里先有头一遍的行
        TSaltSpotRow pre = row("JY00", 3, 0);
        pre.setBatchNo("B14");
        pre.setRowNo(1);
        pre.setStatus(TSaltSpotRow.STATUS_ACCEPTED);
        pre.setDelFlag(0);
        pre.setArchived(0);
        pre.setReportYear(2026);
        rows.put(++idSeq, pre);

        SaltSpotReceipt rc = service.submitBatch("B14",
                java.util.Arrays.asList(row("JY00", 9, 9), row("JY00", 9, 9)));
        assertTrue(rc.isDuplicate());
        assertEquals(1, rc.getTotalRows(), "只照面库内已有的一行");
        assertEquals(1, rows.size(), "不增一行");
    }
}
