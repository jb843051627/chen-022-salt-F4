package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 抽检结果报送册册头对象 t_salt_spot_batch
 *
 * 册码一册一个、进门定死往后不改；回执三个数落在这一处，以库内逐行轧出——
 * 有册头才有回执，杜绝“开库查不到一行，回执却已经发了出去”。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_salt_spot_batch")
@ApiModel(value = "TSaltSpotBatch", description = "抽检结果报送册册头")
public class TSaltSpotBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 监督抽检结果报送册码（进门先领，一册一个，主键在此定死） */
    @TableId(value = "batch_no", type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "监督抽检结果报送册码")
    private String batchNo;

    /** 这册几行（落账时库内轧出） */
    @TableField("total_rows")
    @ApiModelProperty(value = "这册几行")
    private Integer totalRows;

    /** 收下几行 */
    @TableField("accepted_rows")
    @ApiModelProperty(value = "收下几行")
    private Integer acceptedRows;

    /** 退回几行 */
    @TableField("rejected_rows")
    @ApiModelProperty(value = "退回几行")
    private Integer rejectedRows;

    /** 未勾几行（未勾即账不平，不能核讫） */
    @TableField("pending_rows")
    @ApiModelProperty(value = "未勾几行")
    private Integer pendingRows;

    /** 核讫那一栏：核讫 / 空册 */
    @TableField("receipt_mark")
    @ApiModelProperty(value = "核讫栏：核讫/空册")
    private String receiptMark;

    /** 来路：县汇交 / 本处自录（两条来路同一个进门） */
    @TableField("source")
    @ApiModelProperty(value = "来路")
    private String source;

    /** 报送年度（进门那年定死，跨年归档据此判） */
    @TableField("report_year")
    @ApiModelProperty(value = "报送年度")
    private Integer reportYear;

    /** 删除标记 0在册 1已撤（撤册后该册码才许重新进门） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0在册 1已撤")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(Integer acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public Integer getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(Integer rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public Integer getPendingRows() {
        return pendingRows;
    }

    public void setPendingRows(Integer pendingRows) {
        this.pendingRows = pendingRows;
    }

    public String getReceiptMark() {
        return receiptMark;
    }

    public void setReceiptMark(String receiptMark) {
        this.receiptMark = receiptMark;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getReportYear() {
        return reportYear;
    }

    public void setReportYear(Integer reportYear) {
        this.reportYear = reportYear;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
