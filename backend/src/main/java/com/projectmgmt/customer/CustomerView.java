package com.projectmgmt.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CustomerView(
        Long id,
        String shortName,
        String companyName,
        String code,
        String level,
        List<ContactView> contacts,
        List<BankAccountView> bankAccounts,
        @JsonProperty("isPublic") boolean isPublic
) {
    public static CustomerView from(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getShortName(),
                customer.getCompanyName(),
                customer.getCode(),
                customer.getLevel(),
                List.of(),
                List.of(),
                customer.isPublic()
        );
    }

    public static CustomerView detail(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getShortName(),
                customer.getCompanyName(),
                customer.getCode(),
                customer.getLevel(),
                customer.getContacts().stream().map(ContactView::from).toList(),
                customer.getBankAccounts().stream().map(BankAccountView::from).toList(),
                customer.isPublic()
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
        public static ContactView from(CustomerContact contact) {
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
        public static BankAccountView from(CustomerBankAccount account) {
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
