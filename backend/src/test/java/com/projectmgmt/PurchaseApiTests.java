package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Autowired
    SupplierRepository supplierRepository;

    @Test
    void purchaseWarehousePayAndStockFlow() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var manager = staff;
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        String item = "电缆" + suffix;

        String warehouseBody = mockMvc.perform(post("/api/warehouse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试仓%s",
                                  "address": "苏州",
                                  "keeper": "仓管",
                                  "enabled": true
                                }
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("测试仓" + suffix))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer warehouseId = JsonPath.read(warehouseBody, "$.data.id");

        mockMvc.perform(get("/api/warehouse").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + warehouseId + ")].name").exists());

        mockMvc.perform(post("/api/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": %s,
                                  "warehouseId": %s,
                                  "itemName": "%s",
                                  "qty": "10",
                                  "unit": "件",
                                  "amount": "500",
                                  "reason": "未关联项目备货",
                                  "purchaseDate": "2026-08-30"
                                }
                                """.formatted(supplier.getId(), warehouseId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.amount").value(500.00))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/warehouse/purchase-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "itemName": "%s",
                                  "qty": "1",
                                  "unit": "件",
                                  "reason": "入库前出库",
                                  "outDate": "2026-08-30"
                                }
                                """.formatted(warehouseId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));

        String purchaseBody = mockMvc.perform(get("/api/purchase").param("keyword", item).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer purchaseId = JsonPath.read(purchaseBody, "$.data.items[0].id");

        complete(admin, "PURCHASE", purchaseId);

        mockMvc.perform(get("/api/purchase/" + purchaseId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgetDeducted").value(false));

        mockMvc.perform(get("/api/warehouse/" + warehouseId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQty").value(10));

        String outBody = mockMvc.perform(post("/api/warehouse/purchase-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "purchaseId": %s,
                                  "itemName": "%s",
                                  "qty": "3",
                                  "unit": "件",
                                  "reason": "采购退货出库",
                                  "outDate": "2026-08-30"
                                }
                                """.formatted(warehouseId, purchaseId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerPosted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer outId = JsonPath.read(outBody, "$.data.id");
        complete(admin, "STOCK_OUT_PURCHASE", outId);

        mockMvc.perform(get("/api/warehouse/" + warehouseId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQty").value(7));

        String accountBody = mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "采购户%s",
                                  "bankName": "中国银行",
                                  "accountName": "智襄网",
                                  "accountNo": "PO%s",
                                  "openingBalance": 20000,
                                  "enabled": true
                                }
                                """.formatted(suffix, suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer accountId = JsonPath.read(accountBody, "$.data.id");

        mockMvc.perform(post("/api/purchase/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purchaseId": %s,
                                  "accountId": %s,
                                  "amount": "500.01",
                                  "reason": "超未付",
                                  "payDate": "2026-08-30"
                                }
                                """.formatted(purchaseId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未付余额")));

        String payBody = mockMvc.perform(post("/api/purchase/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purchaseId": %s,
                                  "accountId": %s,
                                  "amount": "200",
                                  "reason": "采购付款",
                                  "payDate": "2026-08-30"
                                }
                                """.formatted(purchaseId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerPosted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer payId = JsonPath.read(payBody, "$.data.id");
        complete(admin, "PURCHASE_PAY", payId);

        mockMvc.perform(get("/api/finance/accounts/" + accountId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenseAmount").value(200.00))
                .andExpect(jsonPath("$.data.balance").value(19800.00));

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "采购预算项目")
                        .param("code", "PO-" + suffix)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        String linkedBody = mockMvc.perform(post("/api/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %s,
                                  "supplierId": %s,
                                  "warehouseId": %s,
                                  "itemName": "%s件",
                                  "qty": "2",
                                  "unit": "件",
                                  "amount": "400",
                                  "reason": "关联项目采购",
                                  "purchaseDate": "2026-08-30"
                                }
                                """.formatted(projectId, supplier.getId(), warehouseId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer linkedId = JsonPath.read(linkedBody, "$.data.id");
        complete(admin, "PURCHASE", linkedId);

        String projectOutBody = mockMvc.perform(post("/api/warehouse/project-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "projectId": %s,
                                  "itemName": "%s件",
                                  "qty": "1",
                                  "unit": "件",
                                  "amount": "100",
                                  "reason": "项目领用",
                                  "outDate": "2026-08-30"
                                }
                                """.formatted(warehouseId, projectId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectOutId = JsonPath.read(projectOutBody, "$.data.id");
        complete(admin, "STOCK_OUT_PROJECT", projectOutId);

        mockMvc.perform(get("/api/warehouse/analytics").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.warehouseCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.warehouses[?(@.warehouseId==" + warehouseId + ")].stockQty").exists());

        mockMvc.perform(get("/api/warehouse/analytics").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/purchase/analytics").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.purchaseCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void purchaseLinesPersistAndStockEachItem() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        String cable = "电缆A" + suffix;
        String joint = "接头B" + suffix;

        String warehouseBody = mockMvc.perform(post("/api/warehouse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "明细仓%s",
                                  "address": "苏州",
                                  "keeper": "仓管",
                                  "enabled": true
                                }
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer warehouseId = JsonPath.read(warehouseBody, "$.data.id");

        String purchaseBody = mockMvc.perform(post("/api/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": %s,
                                  "warehouseId": %s,
                                  "reason": "一次采购多种物料",
                                  "purchaseDate": "2026-08-31",
                                  "lines": [
                                    {"itemName": "%s", "qty": "2", "unit": "米", "amount": "200"},
                                    {"itemName": "%s", "qty": "10", "unit": "个", "amount": "50"}
                                  ]
                                }
                                """.formatted(supplier.getId(), warehouseId, cable, joint))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].itemName").value(cable))
                .andExpect(jsonPath("$.data.lines[1].itemName").value(joint))
                .andExpect(jsonPath("$.data.amount").value(250.00))
                .andExpect(jsonPath("$.data.itemName").value(org.hamcrest.Matchers.containsString("等2项")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer purchaseId = JsonPath.read(purchaseBody, "$.data.id");

        mockMvc.perform(get("/api/purchase").param("keyword", joint).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + purchaseId + ")].id").exists());

        complete(admin, "PURCHASE", purchaseId);

        mockMvc.perform(get("/api/warehouse/analytics").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.itemName=='" + cable + "')].stockQty").value(org.hamcrest.Matchers.hasItem(2.0)))
                .andExpect(jsonPath("$.data.items[?(@.itemName=='" + joint + "')].stockQty").value(org.hamcrest.Matchers.hasItem(10.0)));
    }

    @Test
    void warehouseTransferMovesStockBetweenWarehouses() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        String item = "调拨物料" + suffix;

        String sourceBody = mockMvc.perform(post("/api/warehouse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"调出仓%s","address":"苏州","keeper":"仓管","enabled":true}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer sourceId = JsonPath.read(sourceBody, "$.data.id");

        String targetBody = mockMvc.perform(post("/api/warehouse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"调入仓%s","address":"南京","keeper":"仓管","enabled":true}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer targetId = JsonPath.read(targetBody, "$.data.id");

        String purchaseBody = mockMvc.perform(post("/api/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": %s,
                                  "warehouseId": %s,
                                  "itemName": "%s",
                                  "qty": "8",
                                  "unit": "件",
                                  "amount": "80",
                                  "reason": "调拨备货",
                                  "purchaseDate": "2026-08-30"
                                }
                                """.formatted(supplier.getId(), sourceId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer purchaseId = JsonPath.read(purchaseBody, "$.data.id");
        complete(admin, "PURCHASE", purchaseId);

        mockMvc.perform(post("/api/warehouse/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "targetWarehouseId": %s,
                                  "reason": "同仓调拨",
                                  "outDate": "2026-08-31",
                                  "lines": [{"itemName":"%s","qty":"3","unit":"件"}]
                                }
                                """.formatted(sourceId, sourceId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能相同")));

        String transferBody = mockMvc.perform(post("/api/warehouse/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "targetWarehouseId": %s,
                                  "reason": "跨仓调拨",
                                  "outDate": "2026-08-31",
                                  "lines": [{"itemName":"%s","qty":"3","unit":"件"}]
                                }
                                """.formatted(sourceId, targetId, item))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetWarehouseId").value(targetId))
                .andExpect(jsonPath("$.data.ledgerPosted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer transferId = JsonPath.read(transferBody, "$.data.id");
        complete(admin, "STOCK_TRANSFER", transferId);

        mockMvc.perform(get("/api/warehouse/" + sourceId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQty").value(5));
        mockMvc.perform(get("/api/warehouse/" + targetId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQty").value(3));
        mockMvc.perform(get("/api/warehouse/stock-items").param("warehouseId", String.valueOf(sourceId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.itemName=='" + item + "')].stockQty").value(org.hamcrest.Matchers.hasItem(5.0)));
        mockMvc.perform(get("/api/warehouse/transfer").param("keyword", item).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + transferId + ")].id").exists());
    }

    private void complete(com.projectmgmt.user.SysUser admin, String module, Integer bizId) throws Exception {
        WorkflowTestSupport.complete(mockMvc, admin, module, bizId);
    }
}
