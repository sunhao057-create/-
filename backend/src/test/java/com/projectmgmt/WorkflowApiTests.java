package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    SupplierRepository supplierRepository;

    @Autowired
    com.projectmgmt.workflow.WorkflowTaskRepository workflowTaskRepository;

    @Test
    void formWithoutPublishedFlowStaysEditable() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "SUPPLIER")
                        .param("bizId", String.valueOf(supplier.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.canHandle").value(true));
    }

    @Test
    void designNodeFieldModesAndReadRuntime() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "WF" + System.currentTimeMillis();

        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "客户建档流程",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写资料","type":"TASK","x":300,"y":160,"fields":{
                                        "shortName":"EDITABLE","code":"EDITABLE","companyName":"VISIBLE","level":"HIDDEN","contacts":"EDITABLE","bankAccounts":"HIDDEN"
                                      }},
                                      {"key":"end","name":"结束","type":"END","x":520,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill"},
                                      {"from":"fill","to":"end"}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nodes[0].fields.level").value("EDITABLE"))
                .andExpect(jsonPath("$.data.nodes[1].fields.level").value("HIDDEN"))
                .andReturn();

        String body = created.getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));

        mockMvc.perform(get("/api/workflows/runtime").param("module", "CUSTOMER").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.fields.companyName").value("EDITABLE"))
                .andExpect(jsonPath("$.data.fields.level").value("EDITABLE"))
                .andExpect(jsonPath("$.data.fields.shortName").value("EDITABLE"));

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(System.currentTimeMillis()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canAdvance").value(true))
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"));
    }

    @Test
    void startNodeFieldModesControlInitiate() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "ST" + System.currentTimeMillis();

        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "开始节点字段",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{
                                        "shortName":"EDITABLE","code":"VISIBLE","companyName":"HIDDEN","level":"HIDDEN","contacts":"EDITABLE","bankAccounts":"VISIBLE"
                                      }},
                                      {"key":"fill","name":"填写资料","type":"TASK","x":300,"y":160,"fields":{
                                        "shortName":"EDITABLE","code":"EDITABLE","companyName":"EDITABLE","level":"EDITABLE","contacts":"EDITABLE","bankAccounts":"EDITABLE"
                                      }},
                                      {"key":"end","name":"结束","type":"END","x":520,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill"},
                                      {"from":"fill","to":"end"}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].fields.companyName").value("HIDDEN"))
                .andExpect(jsonPath("$.data.nodes[0].fields.code").value("VISIBLE"))
                .andReturn();

        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime").param("module", "CUSTOMER").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.fields.companyName").value("HIDDEN"))
                .andExpect(jsonPath("$.data.fields.code").value("VISIBLE"))
                .andExpect(jsonPath("$.data.fields.shortName").value("EDITABLE"));
    }

    @Test
    void startNodeDraftSavesWithoutSubmitting() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String wfCode = "DR" + System.currentTimeMillis();
        String customerCode = "D" + System.currentTimeMillis();

        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "暂存测试",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"STARTER"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(wfCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "暂存客户",
                                  "companyName": "暂存客户有限公司",
                                  "code": "%s",
                                  "level": "一般重要",
                                  "contacts": [],
                                  "bankAccounts": []
                                }
                                """.formatted(customerCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        Long bizId = customerRepository.findByCode(customerCode).orElseThrow().getId();

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.canAdvance").value(true));

        mockMvc.perform(put("/api/customers/" + bizId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "暂存客户改",
                                  "companyName": "暂存客户有限公司",
                                  "code": "%s",
                                  "level": "一般重要",
                                  "contacts": [],
                                  "bankAccounts": []
                                }
                                """.formatted(customerCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortName").value("暂存客户改"));

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/inbox").param("type", "TODO").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")]").isEmpty());
    }

    @Test
    void inboxAndReturnToPreviousNode() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "RT" + System.currentTimeMillis();
        long bizId = System.currentTimeMillis();

        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "退回测试流程",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"STARTER"},
                                      {"key":"review","name":"审核","type":"APPROVAL","x":480,"y":160,"fields":{},"assigneeType":"STARTER"},
                                      {"key":"end","name":"结束","type":"END","x":680,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"review","isDefault":true},
                                      {"from":"review","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.canAdvance").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/inbox").param("type", "DRAFT").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(admin), "CUSTOMER", bizId);

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("fill"))
                .andExpect(jsonPath("$.data.canAdvance").value(true));

        mockMvc.perform(get("/api/inbox").param("type", "TODO").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"CUSTOMER","bizId":%s,"comment":"提交审核"}
                                """.formatted(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("review"))
                .andExpect(jsonPath("$.data.canReturn").value(true));

        mockMvc.perform(post("/api/workflows/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"CUSTOMER","bizId":%s,"targetNodeKey":"fill","reason":"资料不全"}
                                """.formatted(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("fill"))
                .andExpect(jsonPath("$.data.returnReason").value("资料不全"));

        mockMvc.perform(get("/api/inbox").param("type", "TODO").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")]").isNotEmpty());

        mockMvc.perform(get("/api/inbox").param("type", "RETURNED").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"CUSTOMER","bizId":%s,"comment":"退回后重提"}
                                """.formatted(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("review"))
                .andExpect(jsonPath("$.data.canAdvance").value(true))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"CUSTOMER","bizId":%s,"comment":"审批通过"}
                                """.formatted(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void currentNodeFieldModesBlockHiddenAndVisibleUpdates() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String wfCode = "FM" + System.currentTimeMillis();
        String customerCode = "C" + System.currentTimeMillis();

        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "字段权限流程",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写资料","type":"TASK","x":300,"y":160,"assigneeType":"STARTER","fields":{
                                        "shortName":"EDITABLE","code":"EDITABLE","companyName":"VISIBLE","level":"HIDDEN","contacts":"EDITABLE","bankAccounts":"HIDDEN"
                                      }},
                                      {"key":"end","name":"结束","type":"END","x":520,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill"},
                                      {"from":"fill","to":"end"}
                                    ]
                                  }
                                }
                                """.formatted(wfCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "权限客户",
                                  "companyName": "权限客户有限公司",
                                  "code": "%s",
                                  "level": "一般重要",
                                  "contacts": [
                                    {"name": "原联系人", "title": "对接", "phone": "13900000011", "primaryContact": true}
                                  ],
                                  "bankAccounts": [
                                    {"accountName": "权限客户有限公司", "bankName": "招商银行", "accountNo": "622200011", "primaryAccount": true}
                                  ]
                                }
                                """.formatted(customerCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Long bizId = customerRepository.findByCode(customerCode).orElseThrow().getId();
        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(admin), "CUSTOMER", bizId);

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("fill"))
                .andExpect(jsonPath("$.data.canHandle").value(true))
                .andExpect(jsonPath("$.data.fields.companyName").value("VISIBLE"))
                .andExpect(jsonPath("$.data.fields.level").value("HIDDEN"))
                .andExpect(jsonPath("$.data.fields.shortName").value("EDITABLE"));

        mockMvc.perform(put("/api/customers/" + bizId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "权限客户改",
                                  "companyName": "试图改掉的公司名",
                                  "code": "%s",
                                  "level": "非常重要",
                                  "contacts": [
                                    {"name": "新联系人", "title": "经理", "phone": "13900000012", "primaryContact": true}
                                  ],
                                  "bankAccounts": []
                                }
                                """.formatted(customerCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortName").value("权限客户改"))
                .andExpect(jsonPath("$.data.companyName").value("权限客户有限公司"))
                .andExpect(jsonPath("$.data.level").value("一般重要"))
                .andExpect(jsonPath("$.data.contacts.length()").value(1))
                .andExpect(jsonPath("$.data.contacts[0].name").value("新联系人"))
                .andExpect(jsonPath("$.data.bankAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.bankAccounts[0].accountNo").value("622200011"));
    }

    @Test
    void rejectEmptySpecifiedAssignees() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "UA" + System.currentTimeMillis();

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "指定人员未选",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"审核人","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"USER","assigneeUserIds":[]},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill"},
                                      {"from":"fill","to":"end"}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("审核人 请选择办理人"));
    }

    @Test
    void rejectFormMemberOnModuleWithoutMemberFields() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "PM" + System.currentTimeMillis();

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "付款表单成员",
                                  "code": "%s",
                                  "formModule": "PAYMENT",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写申请","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"FORM_FIELD","assigneeField":"managerId"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill"},
                                      {"from":"fill","to":"end"}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("填写申请 请选择表单中的成员字段"));
    }

    @Test
    void keepDefaultBranchWhenPayloadAlsoHasDefaultKey() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "DF" + System.currentTimeMillis();

        String created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "默认分支",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"STARTER"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edges[0].isDefault").value(true))
                .andExpect(jsonPath("$.data.edges[0].default").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(put("/api/workflows/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "默认分支",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{},"assigneeType":null,"assigneeUserIds":[]},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"STARTER","assigneeUserIds":[]},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{},"assigneeType":null,"assigneeUserIds":[]}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","name":null,"default":false,"isDefault":true,"conditions":[]},
                                      {"from":"fill","to":"end","name":null,"default":false,"isDefault":true,"conditions":[]}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edges[0].isDefault").value(true))
                .andExpect(jsonPath("$.data.edges[1].isDefault").value(true));
    }

    @Test
    void approvalTimeoutIsSavedAndInvalidUnitRejected() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "TO" + System.currentTimeMillis();
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "超时流程",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":8,"timeoutUnit":"HOUR"},
                                      {"key":"cc","name":"抄送","type":"CC","x":480,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":3,"timeoutUnit":"DAY"},
                                      {"key":"end","name":"结束","type":"END","x":680,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"cc","isDefault":true},
                                      {"from":"cc","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[?(@.key=='fill')].timeoutValue")
                        .value(org.hamcrest.Matchers.hasItem(8)))
                .andExpect(jsonPath("$.data.nodes[?(@.key=='fill')].timeoutUnit")
                        .value(org.hamcrest.Matchers.hasItem("HOUR")))
                .andExpect(jsonPath("$.data.nodes[?(@.key=='cc')].timeoutValue")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "非法超时",
                                  "code": "%sX",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":8,"timeoutUnit":"WEEK"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("超时时限单位只能是小时或天"));
    }

    @Test
    void timeoutMarksInboxAndRuntimeOverdue() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "TD" + System.currentTimeMillis();
        long bizId = System.currentTimeMillis();
        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "超时待办",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":8,"timeoutUnit":"HOUR"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(admin), "CUSTOMER", bizId);

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("fill"))
                .andExpect(jsonPath("$.data.dueAt").exists())
                .andExpect(jsonPath("$.data.overdue").value(false));

        String inbox = mockMvc.perform(get("/api/inbox").param("type", "TODO").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")].overdue")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")].dueAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.List<Number> ids = com.jayway.jsonpath.JsonPath.read(inbox, "$.data[?(@.bizId==" + bizId + ")].id");
        long taskId = ids.get(0).longValue();

        var task = workflowTaskRepository.findById(taskId).orElseThrow();
        task.setDueAt(java.time.LocalDateTime.now().minusHours(1));
        workflowTaskRepository.save(task);

        mockMvc.perform(get("/api/inbox").param("type", "TODO").param("overdue", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")].overdue")
                        .value(org.hamcrest.Matchers.hasItem(true)));
        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.overdue").value(true));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"CUSTOMER","bizId":%s,"comment":"办完"}
                                """.formatted(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inbox").param("type", "TODO").param("overdue", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")]").isEmpty());
    }
}
