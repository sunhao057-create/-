package com.projectmgmt.supplier;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SupplierView(
        Long id,
        String shortName,
        String companyName,
        String code,
        String category,
        List<ContactView> contacts,
        List<BankAccountView> bankAccounts,
        @JsonProperty("isPublic") boolean isPublic
) {
    public static SupplierView from(Supplier supplier) {
        return new SupplierView(
                supplier.getId(),
                supplier.getShortName(),
                supplier.getCompanyName(),
                supplier.getCode(),
                supplier.getCategory(),
                List.of(),
                List.of(),
                supplier.isPublic()
        );
    }

    public static SupplierView detail(Supplier supplier) {
        return new SupplierView(
                supplier.getId(),
                supplier.getShortName(),
                supplier.getCompanyName(),
                supplier.getCode(),
                supplier.getCategory(),
                supplier.getContacts().stream().map(ContactView::from).toList(),
                supplier.getBankAccounts().stream().map(BankAccountView::from).toList(),
                supplier.isPublic()
        );
    }

    public record ContactView(
            Long id,
            String name,
            String title,
            String phone,
            String email,
            boolean primaryContact
    ) {
        public static ContactView from(SupplierContact contact) {
            return new ContactView(
                    contact.getId(),
                    contact.getName(),
                    contact.getTitle(),
                    contact.getPhone(),
                    contact.getEmail(),
                    contact.isPrimaryContact()
            );
        }
    }

    public record BankAccountView(
            Long id,
            String accountName,
            String bankName,
            String accountNo,
            String branch,
            boolean primaryAccount
    ) {
        public static BankAccountView from(SupplierBankAccount account) {
            return new BankAccountView(
                    account.getId(),
                    account.getAccountName(),
                    account.getBankName(),
                    account.getAccountNo(),
                    account.getBranch(),
                    account.isPrimaryAccount()
            );
        }
    }
}
