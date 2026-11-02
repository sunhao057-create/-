package com.projectmgmt.deposit;

import com.projectmgmt.role.FormModule;

public enum DepositKind {
    APPLY("项目保证金申请"),
    RETURN("项目保证金退回");

    private final String label;

    DepositKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public FormModule module() {
        return this == APPLY ? FormModule.DEPOSIT_APPLY : FormModule.DEPOSIT_RETURN;
    }

    public String codePrefix() {
        return this == APPLY ? "BZ" : "BZT";
    }

    public static DepositKind fromModule(FormModule module) {
        if (module == FormModule.DEPOSIT_APPLY) {
            return APPLY;
        }
        if (module == FormModule.DEPOSIT_RETURN) {
            return RETURN;
        }
        return null;
    }
}
