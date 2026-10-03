package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.model.custom.SaltSpotReceipt;
import com.fc.v2.model.custom.SaltSpotRowCount;

/**
 * 抽检结果报送核收行 Service接口。
 *
 * 形状：册子进门的路径只有一条，落在业务服务层——从册头校验到逐行处置到轧出回执，一趟走完。
 * 册内单行没有自己的新增口、改动口；“同册码第二遍只照面、不记账”也只在这一处成立，页面不得自持一套。
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface ITSaltSpotRowService {

    /** 来路：各县汇交 */
    String SOURCE_COUNTY = "县汇交";
    /** 来路：本处自录 */
    String SOURCE_SELF = "本处自录";

    /** 按主键回查单行（只读口） */
    TSaltSpotRow selectTSaltSpotRowById(Long id);

    /**
     * 整册进门（参数表照旧：册码 + 整册行）。册头校验 → 逐行处置 → 库内轧回执，一趟走完：
     * 1）册码一册一个：同一册码第二遍进来只照面——不增一行、不减一行，头一遍的结果谁也覆不了；要改先撤册。
     * 2）册头先数行：行数压过每册行顶，整册原样退回，库里一行不留；一行也没有的，核讫栏写“空册”，不发核讫。
     * 3）逐行：行次由系统按落库次序排，排定不挪，驳回次日再翻仍是原行次；
     *    送检件数、不合格件数须同时递来，缺一个不替它补（行落库、去向留“未勾”）；
     *    只认企业代号对名录，拿企业名对的一律算没对上，原路退回、缘由写行上；
     *    不合格件数压过当季限值的，只退本行，同册其余各行照旧。
     * 4）回执三个数以库内为准逐行轧出；收下+退回≠总行时当场列差行，不算交账。
     */
    SaltSpotReceipt submitBatch(String batchNo, List<TSaltSpotRow> rows);

    /**
     * 按页收册重载：同名方法多挂一层，壳内仍走上面那一道门——县汇交、本处自录两条来路同口，
     * 第二遍只照面的判断也仍只在服务层这一处。页只是收束的样子，不另开第二遍发生地。
     *
     * @param batchNo 册码
     * @param source  来路（{@link #SOURCE_COUNTY} / {@link #SOURCE_SELF}）
     * @param rows    本页收上来的行（行次仍由库内落序排，不许页上自带）
     */
    SaltSpotReceipt submitBatch(String batchNo, String source, List<SaltSpotRowCount> rows);

    /** 翻一册的行（现行名单与往期层都翻得着，一条不许少；按行次升序） */
    List<TSaltSpotRow> listByBatch(String batchNo);

    /** 以库内为准重新轧一册的回执（任抽一行复核，行上去向与回执数字出自同一次落账） */
    SaltSpotReceipt receipt(String batchNo);

    /**
     * 撤册手续：整册从库内勾销，勾销之后该册码才许重新进门。不撤册的，第二遍永远只照面。
     * @return 勾销行数
     */
    int revokeBatch(String batchNo);

    /**
     * 跨年兜底：去向还空着（未勾）、到第二个报送年度仍没着落的行，迁去往期层；
     * 往期层照样翻得着、一条不许少；当年新册不动。
     * @param currentYear 当前报送年度
     * @return 迁移行数
     */
    int archiveStalePending(int currentYear);
}
