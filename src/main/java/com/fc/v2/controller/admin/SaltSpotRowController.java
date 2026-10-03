package com.fc.v2.controller.admin;

import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.model.custom.SaltSpotReceipt;
import com.fc.v2.model.custom.SaltSpotRowCount;
import com.fc.v2.service.ITSaltSpotRowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 食盐监督抽检结果报送 Controller。
 *
 * 册子进门的路径只有一条，落在业务服务层：从册头校验到逐行处置到轧出回执一趟走完。
 * 接口层不摆单行的新增口、改动口；“同册码第二遍只照面”不在页面自持一套；
 * 回执三个数取自服务层那一趟的返回，不取自页面先亮出来的数。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "食盐监督抽检结果报送")
@Controller
@RequestMapping("/saltSpotRow")
public class SaltSpotRowController extends BaseController {

    @Autowired
    private ITSaltSpotRowService saltSpotRowService;

    @Log(title = "抽检结果册整册进门", action = "submit")
    @ApiOperation(value = "整册进门", notes = "整册一次导入；各县汇交与本处自录同一个进门")
    @PostMapping("/submit")
    @RequiresPermissions("saltSpotRow:submit")
    @ResponseBody
    public AjaxResult submit(@RequestParam("batchNo") String batchNo,
                             @RequestBody List<TSaltSpotRow> rows) {
        SaltSpotReceipt receipt = saltSpotRowService.submitBatch(batchNo, rows);
        return AjaxResult.successData(200, receipt);
    }

    @Log(title = "抽检结果册按页进门", action = "submitPage")
    @ApiOperation(value = "按页收册（同名方法多挂一层）", notes = "壳内仍走整册那一道门，第二遍判断只在服务层")
    @PostMapping("/submitPage")
    @RequiresPermissions("saltSpotRow:submit")
    @ResponseBody
    public AjaxResult submit(@RequestParam("batchNo") String batchNo,
                             @RequestParam(value = "source", required = false) String source,
                             @RequestBody List<SaltSpotRowCount> rows) {
        SaltSpotReceipt receipt = saltSpotRowService.submitBatch(batchNo, source, rows);
        return AjaxResult.successData(200, receipt);
    }

    @ApiOperation(value = "回执复核", notes = "以库内为准重新轧一册的回执")
    @GetMapping("/receipt")
    @RequiresPermissions("saltSpotRow:view")
    @ResponseBody
    public AjaxResult receipt(@RequestParam("batchNo") String batchNo) {
        SaltSpotReceipt receipt = saltSpotRowService.receipt(batchNo);
        if (receipt == null) {
            return AjaxResult.error("在册册头不存在，没有可发的回执");
        }
        return AjaxResult.successData(200, receipt);
    }

    @ApiOperation(value = "翻册内行", notes = "现行名单与往期层都翻得着，一条不许少")
    @GetMapping("/rows")
    @RequiresPermissions("saltSpotRow:view")
    @ResponseBody
    public AjaxResult rows(@RequestParam("batchNo") String batchNo) {
        return AjaxResult.successData(200, saltSpotRowService.listByBatch(batchNo));
    }

    @Log(title = "抽检结果册撤册", action = "revoke")
    @ApiOperation(value = "撤册", notes = "先办撤册手续，册码腾出后才许重新进门")
    @PostMapping("/revoke")
    @RequiresPermissions("saltSpotRow:revoke")
    @ResponseBody
    public AjaxResult revoke(@RequestParam("batchNo") String batchNo) {
        int n = saltSpotRowService.revokeBatch(batchNo);
        return AjaxResult.success("撤册勾销" + n + "行");
    }

    @Log(title = "往期归档", action = "archive")
    @ApiOperation(value = "跨年归档", notes = "到第二个报送年度仍未勾的旧册行迁往期层；新册不适用")
    @PostMapping("/archive")
    @RequiresPermissions("saltSpotRow:archive")
    @ResponseBody
    public AjaxResult archive(@RequestParam("year") int year) {
        int n = saltSpotRowService.archiveStalePending(year);
        return AjaxResult.success("迁入往期层" + n + "行");
    }
}
