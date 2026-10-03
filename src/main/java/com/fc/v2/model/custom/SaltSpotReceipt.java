package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 抽检结果报送册回执：一趟进门走完后的交账。
 * 三个数（总行数/收下/退回）一律以库内逐行轧出，不取页面先亮出来的数；
 * 行数为零的册核讫栏写“空册”，不用“核讫”。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ApiModel(value = "SaltSpotReceipt", description = "抽检结果报送册回执")
public class SaltSpotReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 核讫栏说法 */
    public static final String MARK_CHECKED = "核讫";
    public static final String MARK_EMPTY = "空册";
    /** 超顶整册退回时库里一行不留，没有核讫栏可填 */
    public static final String MARK_TURNED_AWAY = "整册退回";

    @ApiModelProperty(value = "册码（进门定死，往后不改）")
    private String batchNo;

    @ApiModelProperty(value = "这册几行（库内轧出）")
    private int totalRows;

    @ApiModelProperty(value = "收下几行（库内轧出）")
    private int acceptedRows;

    @ApiModelProperty(value = "退回几行（库内轧出）")
    private int rejectedRows;

    @ApiModelProperty(value = "核讫那一栏：核讫/空册/整册退回")
    private String receiptMark;

    @ApiModelProperty(value = "是否同册码第二遍（只照面、不记账）")
    private boolean duplicate;

    @ApiModelProperty(value = "未落地随回执退回的行（带纸面上的原行次与缘由）")
    private List<Line> returnedLines = new ArrayList<Line>();

    @ApiModelProperty(value = "轧账不平当场列出的差行；非空即不算交账")
    private List<Line> imbalanceLines = new ArrayList<Line>();

    /** 未落地行带回纸面行次与缘由 */
    public static class Line implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 纸面上的原行次（=系统落库序排定的行次，驳回次日再翻不变） */
        private Integer rowNo;
        private String itemCode;
        private String reason;

        public Line() {
        }

        public Line(Integer rowNo, String itemCode, String reason) {
            this.rowNo = rowNo;
            this.itemCode = itemCode;
            this.reason = reason;
        }

        public Integer getRowNo() {
            return rowNo;
        }

        public void setRowNo(Integer rowNo) {
            this.rowNo = rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(int acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(int rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public String getReceiptMark() {
        return receiptMark;
    }

    public void setReceiptMark(String receiptMark) {
        this.receiptMark = receiptMark;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public List<Line> getReturnedLines() {
        return returnedLines;
    }

    public void setReturnedLines(List<Line> returnedLines) {
        this.returnedLines = returnedLines;
    }

    public List<Line> getImbalanceLines() {
        return imbalanceLines;
    }

    public void setImbalanceLines(List<Line> imbalanceLines) {
        this.imbalanceLines = imbalanceLines;
    }
}
