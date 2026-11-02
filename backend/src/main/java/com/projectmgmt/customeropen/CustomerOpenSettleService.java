package com.projectmgmt.customeropen;

import com.projectmgmt.role.FormModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerOpenSettleService {

    private final CustomerOpenService customerOpenService;
    private final CustomerAccountOpenService customerAccountOpenService;

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == FormModule.CUSTOMER_APPLY) {
            customerOpenService.settle(bizId);
        } else if (module == FormModule.CUSTOMER_ACCOUNT) {
            customerAccountOpenService.settle(bizId);
        }
    }
}
