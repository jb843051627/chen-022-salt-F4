package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 抽检册内一行的进门报项（不是实体：行没有自己的落库口，行次由系统按落库次序排，不许报册自带）
 *
 * @author fuce
 * @date 2026-10-03
 */
@ApiModel(value = "SaltSpotRowCount", description = "抽检册内一行：企业代号+送检件数+不合格件数（两数须齐）")
public class SaltSpotRowCount implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 被报的定点企业代号（只认代号，拿企业名对的一律算没对上） */
    @ApiModelProperty(value = "定点企业代号（只认代号，不认企业名）")
    private String itemCode;

    /** 企业全称（只作随册记载，绝不拿它对名录） */
    @ApiModelProperty(value = "企业全称（仅随册记载，不参与对上）")
    private String itemName;

    /** 本行送检件数（与不合格件数必须同时递来，缺一个都不许替它补） */
    @ApiModelProperty(value = "本行送检件数（必填）")
    private BigDecimal sampleCount;

    /** 本行不合格件数 */
    @ApiModelProperty(value = "本行不合格件数（必填）")
    private BigDecimal failCount;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public BigDecimal getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(BigDecimal sampleCount) {
        this.sampleCount = sampleCount;
    }

    public BigDecimal getFailCount() {
        return failCount;
    }

    public void setFailCount(BigDecimal failCount) {
        this.failCount = failCount;
    }
}
