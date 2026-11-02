package com.projectmgmt.config;

import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerBankAccount;
import com.projectmgmt.customer.CustomerContact;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.supplier.Supplier;
import com.projectmgmt.supplier.SupplierBankAccount;
import com.projectmgmt.supplier.SupplierContact;
import com.projectmgmt.supplier.SupplierRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修复历史上把 UTF-8 字节按 Latin-1 再存进 utf8mb4 造成的双重编码乱码。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class MojibakeRepairer implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int fixed = 0;
        for (Customer customer : customerRepository.findAll()) {
            customer.setShortName(decode(customer.getShortName()));
            customer.setCompanyName(decode(customer.getCompanyName()));
            customer.setLevel(decode(customer.getLevel()));
            if (customer.getContacts() != null) {
                for (CustomerContact contact : customer.getContacts()) {
                    contact.setName(decode(contact.getName()));
                    contact.setTitle(decode(contact.getTitle()));
                }
            }
            if (customer.getBankAccounts() != null) {
                for (CustomerBankAccount account : customer.getBankAccounts()) {
                    account.setAccountName(decode(account.getAccountName()));
                    account.setBankName(decode(account.getBankName()));
                    account.setBranch(decode(account.getBranch()));
                }
            }
            fixed++;
        }
        for (Supplier supplier : supplierRepository.findAll()) {
            supplier.setShortName(decode(supplier.getShortName()));
            supplier.setCompanyName(decode(supplier.getCompanyName()));
            supplier.setCategory(decode(supplier.getCategory()));
            if (supplier.getContacts() != null) {
                for (SupplierContact contact : supplier.getContacts()) {
                    contact.setName(decode(contact.getName()));
                    contact.setTitle(decode(contact.getTitle()));
                }
            }
            if (supplier.getBankAccounts() != null) {
                for (SupplierBankAccount account : supplier.getBankAccounts()) {
                    account.setAccountName(decode(account.getAccountName()));
                    account.setBankName(decode(account.getBankName()));
                    account.setBranch(decode(account.getBranch()));
                }
            }
        }
        for (Project project : projectRepository.findAll()) {
            project.setName(decode(project.getName()));
            project.setCompanyName(decode(project.getCompanyName()));
            project.setCompanyLevel(decode(project.getCompanyLevel()));
            project.setOverview(decode(project.getOverview()));
        }
        log.info("已检查 {} 条客户记录的中文编码", fixed);
    }

    static String decode(String value) {
        if (value == null || value.isEmpty() || !looksDoubleEncoded(value)) {
            return value;
        }
        try {
            String fixed = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            if (fixed.contains("\uFFFD") || !hasCjk(fixed)) {
                return value;
            }
            return fixed;
        } catch (Exception ignored) {
            return value;
        }
    }

    static boolean looksDoubleEncoded(String value) {
        int latin1 = 0;
        int cjk = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x80 && ch <= 0xFF) {
                latin1++;
            } else if (ch >= 0x4E00 && ch <= 0x9FFF) {
                cjk++;
            }
        }
        return latin1 >= 2 && cjk == 0;
    }

    private static boolean hasCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }
}
