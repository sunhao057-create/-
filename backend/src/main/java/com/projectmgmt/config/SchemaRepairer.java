package com.projectmgmt.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaRepairer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, "wf_instance")) {
                ensureColumn(connection, "wf_instance", "created_at", "datetime(6) NULL");
                ensureColumn(connection, "wf_instance", "visited_keys", "json NULL");
            }
            if (tableExists(connection, "wf_task")) {
                ensureColumn(connection, "wf_task", "due_at", "datetime(6) NULL");
            }
            dropBudgetTables(connection);
            ensureModuleVarchar(connection, "sys_role_permission", "module");
            ensureModuleVarchar(connection, "wf_definition", "form_module");
            ensureModuleVarchar(connection, "wf_instance", "form_module");
            deleteObsoleteModule(connection, "BUDGET");
            deleteObsoleteModule(connection, "COLLECTION");
            ensureModuleVarchar(connection, "wh_stock_out", "kind");
            ensureModuleVarchar(connection, "wh_stock_txn", "source_type");
            deleteObsoleteModule(connection, "CUSTOMER_ANALYTICS");
            ensurePermissionColumns(connection);
            ensureCreatedBy(connection, "crm_customer");
            ensureCreatedBy(connection, "crm_supplier");
            ensurePublicColumns(connection, "pm_project");
            ensurePublicColumns(connection, "pm_payment");
            if (tableExists(connection, "oa_request")) {
                ensureColumn(connection, "oa_request", "link_request_id", "bigint NULL");
            }
            if (tableExists(connection, "pm_payment")) {
                ensureColumn(connection, "pm_payment", "account_id", "bigint NULL");
                ensureColumnNullable(connection, "pm_payment", "project_id");
            }
            if (tableExists(connection, "crm_contract_receipt")) {
                ensureColumn(connection, "crm_contract_receipt", "account_id", "bigint NULL");
            }
            if (tableExists(connection, "pm_project")) {
                ensureColumn(connection, "pm_project", "progress_percent", "int NULL");
            }
            ensureProgressTables(connection);
            ensureOrgTables(connection);
            ensureOaAccountColumns(connection);
            ensurePurchaseLineTable(connection);
            ensureStockTransferColumns(connection);
            ensureKindAsVarchar(connection, "eq_device", "status", 16);
            ensureKindAsVarchar(connection, "eq_work_order", "kind", 16);
            ensureKindAsVarchar(connection, "eq_work_order", "snapshot_status", 16);
            ensureKindAsVarchar(connection, "pm_project", "status", 30);
            ensureKindAsVarchar(connection, "crm_contract", "status", 20);
            ensureKindAsVarchar(connection, "pm_deposit", "kind", 16);
            ensureKindAsVarchar(connection, "pm_bid", "result", 16);
            ensureKindAsVarchar(connection, "pm_bid", "round2_result", 16);
            ensureKindAsVarchar(connection, "pm_bid_file", "category", 16);
            if (tableExists(connection, "eq_device")) {
                ensureColumn(connection, "eq_device", "photo_url", "varchar(255) NULL");
            }
            ensureDeviceWorkOrderFileTable(connection);
            ensureTechTables(connection);
            if (tableExists(connection, "sys_branding")) {
                ensureColumn(connection, "sys_branding", "theme_id", "varchar(32) NOT NULL DEFAULT 'minimal'");
            }
            if (tableExists(connection, "pm_project")) {
                ensureColumn(connection, "pm_project", "contract_id", "bigint NULL");
            }
        }
    }

    private void dropBudgetTables(Connection connection) throws SQLException {
        for (String table : new String[] {"pm_budget_file", "pm_budget_change", "pm_project_budget"}) {
            if (!tableExists(connection, table)) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.execute("DROP TABLE IF EXISTS " + table + " CASCADE");
                } catch (SQLException ignored) {
                    statement.execute("DROP TABLE IF EXISTS " + table);
                }
                log.info("已删除项目预算表 {}", table);
            }
        }
    }

    private void ensureModuleVarchar(Connection connection, String table, String column) throws SQLException {
        if (!tableExists(connection, table) || !columnExists(connection, table, column)) {
            return;
        }
        String typeName = columnTypeName(connection, table, column);
        if (typeName != null && (typeName.toLowerCase().contains("varchar") || typeName.toLowerCase().contains("character"))) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DATA TYPE VARCHAR(32)");
            } catch (SQLException ignored) {
                statement.execute("ALTER TABLE " + table + " MODIFY " + column + " varchar(32)");
            }
            log.info("已将 {}.{} 改为 varchar，便于新增表单模块", table, column);
        } catch (SQLException ex) {
            log.warn("未能将 {}.{} 改为 varchar: {}", table, column, ex.getMessage());
        }
    }

    private void ensureKindAsVarchar(Connection connection, String table, String column, int length)
            throws SQLException {
        if (!tableExists(connection, table) || !columnExists(connection, table, column)) {
            return;
        }
        String ddl = "varchar(" + length + ")";
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DATA TYPE " + ddl);
            } catch (SQLException ignored) {
                try {
                    statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " " + ddl);
                } catch (SQLException ignored2) {
                    statement.execute("ALTER TABLE " + table + " MODIFY " + column + " " + ddl);
                }
            }
            log.info("已将 {}.{} 调整为 {}，以支持新增单据类型", table, column, ddl);
        } catch (SQLException ex) {
            log.warn("未能调整 {}.{}: {}", table, column, ex.getMessage());
        }
    }

    private void deleteObsoleteModule(Connection connection, String module) throws SQLException {
        String[][] targets = {
            {"sys_role_permission", "module"},
            {"wf_definition", "form_module"},
            {"wf_instance", "form_module"},
        };
        for (String[] target : targets) {
            if (!tableExists(connection, target[0]) || !columnExists(connection, target[0], target[1])) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                int removed = statement.executeUpdate(
                        "DELETE FROM " + target[0] + " WHERE " + target[1] + " = '" + module + "'");
                if (removed > 0) {
                    log.info("已删除过时模块 {} 在 {}.{} 共 {} 行", module, target[0], target[1], removed);
                }
            }
        }
    }

    private String columnTypeName(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        for (String tableName : new String[] {table, table.toUpperCase()}) {
            for (String columnName : new String[] {column, column.toUpperCase()}) {
                try (ResultSet rs = meta.getColumns(catalog, null, tableName, columnName)) {
                    if (rs.next()) {
                        return rs.getString("TYPE_NAME");
                    }
                }
            }
        }
        return null;
    }

    private void ensurePermissionColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, "sys_role_permission")) {
            return;
        }
        boolean addedCreate = addColumnIfMissing(
                connection, "sys_role_permission", "can_create", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(
                connection, "sys_role_permission", "data_scope", "varchar(16) NOT NULL DEFAULT 'ALL'");
        addColumnIfMissing(
                connection, "sys_role_permission", "can_public", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(
                connection, "sys_role_permission", "can_public_display", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(
                connection, "sys_role_permission", "can_public_submit", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(
                connection, "sys_role_permission", "can_public_query", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(
                connection, "sys_role_permission", "can_public_export", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(
                connection, "sys_role_permission", "can_flow", "tinyint(1) NOT NULL DEFAULT 0");
        if (addedCreate || shouldBackfillCreate(connection)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("UPDATE sys_role_permission SET can_create = can_edit");
                log.info("已将原编辑权限同步为新增权限 sys_role_permission.can_create");
            }
        }
    }

    private boolean shouldBackfillCreate(Connection connection) throws SQLException {
        if (!columnExists(connection, "sys_role_permission", "can_create")
                || !columnExists(connection, "sys_role_permission", "can_edit")) {
            return false;
        }
        int withCreate = count(connection, "SELECT COUNT(*) FROM sys_role_permission WHERE can_create <> 0");
        int withEdit = count(connection, "SELECT COUNT(*) FROM sys_role_permission WHERE can_edit <> 0");
        return withCreate == 0 && withEdit > 0;
    }

    private void ensureProgressTables(Connection connection) throws SQLException {
        if (!tableExists(connection, "pm_project") || tableExists(connection, "pm_project_progress")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE pm_project_progress (
                      id bigint NOT NULL AUTO_INCREMENT,
                      project_id bigint NOT NULL,
                      content varchar(2000) NOT NULL,
                      percent int NOT NULL,
                      submit_source varchar(16) NOT NULL,
                      created_by bigint NULL,
                      creator_name varchar(50) NOT NULL,
                      created_at datetime(6) NOT NULL,
                      PRIMARY KEY (id),
                      KEY IDX_pm_project_progress_project (project_id),
                      CONSTRAINT FK_pm_project_progress_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute(
                    """
                    CREATE TABLE pm_project_progress_file (
                      id bigint NOT NULL AUTO_INCREMENT,
                      progress_id bigint NOT NULL,
                      original_name varchar(255) NOT NULL,
                      url varchar(255) NOT NULL,
                      size bigint NOT NULL,
                      content_type varchar(100) NULL,
                      PRIMARY KEY (id),
                      KEY IDX_pm_project_progress_file (progress_id),
                      CONSTRAINT FK_pm_project_progress_file FOREIGN KEY (progress_id) REFERENCES pm_project_progress (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
        log.info("已创建项目进展表");
    }

    private void ensureOrgTables(Connection connection) throws SQLException {
        if (!tableExists(connection, "sys_org_dept")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE sys_org_dept (
                          id bigint NOT NULL AUTO_INCREMENT,
                          name varchar(80) NOT NULL,
                          parent_id bigint NULL,
                          sort_order int NOT NULL DEFAULT 0,
                          PRIMARY KEY (id),
                          KEY IDX_sys_org_dept_parent (parent_id),
                          CONSTRAINT FK_sys_org_dept_parent FOREIGN KEY (parent_id) REFERENCES sys_org_dept (id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建组织部门表 sys_org_dept");
        }
        if (tableExists(connection, "sys_user")) {
            ensureColumn(connection, "sys_user", "department_id", "bigint NULL");
            ensureColumn(connection, "sys_user", "is_dept_leader", "tinyint(1) NOT NULL DEFAULT 0");
            ensureColumn(connection, "sys_user", "position", "varchar(80) NULL");
            ensureColumn(connection, "sys_user", "job_level", "varchar(40) NULL");
            ensureColumn(connection, "sys_user", "salary", "decimal(18,2) NULL");
            ensureColumn(connection, "sys_user", "gender", "varchar(16) NULL");
            ensureColumn(connection, "sys_user", "id_number", "varchar(32) NULL");
            ensureColumn(connection, "sys_user", "mobile", "varchar(20) NULL");
            ensureColumn(connection, "sys_user", "birth_date", "date NULL");
            ensureColumn(connection, "sys_user", "hire_date", "date NULL");
        }
        if (tableExists(connection, "sys_user") && !tableExists(connection, "sys_user_salary_item")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE sys_user_salary_item (
                          id bigint NOT NULL AUTO_INCREMENT,
                          user_id bigint NOT NULL,
                          item_name varchar(80) NOT NULL,
                          amount decimal(18,2) NOT NULL,
                          sort_order int NOT NULL,
                          PRIMARY KEY (id),
                          KEY IDX_sys_user_salary_item_user (user_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建用户工资组成表 sys_user_salary_item");
        }
    }

    private void ensureOaAccountColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, "oa_request")) {
            return;
        }
        ensureColumn(connection, "oa_request", "login_username", "varchar(64) NULL");
        ensureColumn(connection, "oa_request", "password_hash", "varchar(120) NULL");
        ensureColumn(connection, "oa_request", "applied", "tinyint(1) NOT NULL DEFAULT 0");
        ensureColumn(connection, "oa_request", "salary", "decimal(18,2) NULL");
        ensureColumn(connection, "oa_request", "birth_date", "date NULL");
        ensureColumn(connection, "oa_request", "ethnicity", "varchar(40) NULL");
        ensureColumn(connection, "oa_request", "marital_status", "varchar(20) NULL");
        ensureColumn(connection, "oa_request", "email", "varchar(120) NULL");
        ensureColumn(connection, "oa_request", "hukou_address", "varchar(200) NULL");
        ensureColumn(connection, "oa_request", "current_address", "varchar(200) NULL");
        ensureColumn(connection, "oa_request", "school", "varchar(120) NULL");
        ensureColumn(connection, "oa_request", "major", "varchar(80) NULL");
        ensureColumn(connection, "oa_request", "highest_education", "varchar(40) NULL");
        ensureColumn(connection, "oa_request", "education_note", "varchar(80) NULL");
        ensureColumn(connection, "oa_request", "extra_json", "text NULL");
        ensureKindAsVarchar(connection, "oa_request", "kind", 20);
        if (!tableExists(connection, "oa_salary_line")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE oa_salary_line (
                          id bigint NOT NULL AUTO_INCREMENT,
                          request_id bigint NOT NULL,
                          item_name varchar(80) NOT NULL,
                          amount decimal(18,2) NOT NULL,
                          sort_order int NOT NULL,
                          PRIMARY KEY (id),
                          KEY IDX_oa_salary_line_request (request_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建 OA 工资组成表 oa_salary_line");
        }
        if (!tableExists(connection, "oa_resume_line")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE oa_resume_line (
                          id bigint NOT NULL AUTO_INCREMENT,
                          request_id bigint NOT NULL,
                          line_kind varchar(20) NOT NULL,
                          title varchar(120) NULL,
                          subtitle varchar(120) NULL,
                          extra varchar(200) NULL,
                          start_ym varchar(7) NULL,
                          end_ym varchar(7) NULL,
                          sort_order int NOT NULL,
                          PRIMARY KEY (id),
                          KEY IDX_oa_resume_line_request (request_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建 OA 履历表 oa_resume_line");
        }
    }

    private void ensureStockTransferColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, "wh_stock_out")) {
            return;
        }
        ensureColumn(connection, "wh_stock_out", "target_warehouse_id", "bigint NULL");
        ensureKindAsVarchar(connection, "wh_stock_out", "kind", 16);
        ensureKindAsVarchar(connection, "wh_stock_txn", "source_type", 20);
        if (!tableExists(connection, "wh_stock_out_line")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE wh_stock_out_line (
                          id bigint NOT NULL AUTO_INCREMENT,
                          stock_out_id bigint NOT NULL,
                          item_name varchar(120) NOT NULL,
                          qty decimal(18,3) NOT NULL,
                          unit varchar(20) NOT NULL,
                          amount decimal(18,2) DEFAULT NULL,
                          sort_order int NOT NULL,
                          PRIMARY KEY (id),
                          KEY IDX_wh_stock_out_line_out (stock_out_id),
                          CONSTRAINT FK_wh_stock_out_line_out FOREIGN KEY (stock_out_id) REFERENCES wh_stock_out (id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建出库/调拨明细表 wh_stock_out_line");
        }
    }

    private void ensureDeviceWorkOrderFileTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "eq_work_order") || tableExists(connection, "eq_work_order_file")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE eq_work_order_file (
                      id bigint NOT NULL AUTO_INCREMENT,
                      work_order_id bigint NOT NULL,
                      original_name varchar(255) NOT NULL,
                      url varchar(255) NOT NULL,
                      size bigint NOT NULL,
                      content_type varchar(100) DEFAULT NULL,
                      PRIMARY KEY (id),
                      KEY IDX_eq_work_order_file_order (work_order_id),
                      CONSTRAINT FK_eq_work_order_file_order FOREIGN KEY (work_order_id) REFERENCES eq_work_order (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
        log.info("已创建设备工单附件表 eq_work_order_file");
    }

    private void ensureTechTables(Connection connection) throws SQLException {
        if (!tableExists(connection, "ts_record")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE ts_record (
                          id bigint NOT NULL AUTO_INCREMENT,
                          code varchar(32) NOT NULL,
                          kind varchar(40) NOT NULL,
                          title varchar(200) NOT NULL,
                          extra_json text NULL,
                          is_public tinyint(1) NOT NULL DEFAULT 0,
                          submit_source varchar(16) NOT NULL DEFAULT 'ADMIN',
                          created_by bigint NULL,
                          created_at datetime(6) NOT NULL,
                          updated_at datetime(6) NOT NULL,
                          PRIMARY KEY (id),
                          UNIQUE KEY UK_ts_record_code (code),
                          KEY IDX_ts_record_kind (kind)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建技术服务表 ts_record");
        }
        if (!tableExists(connection, "ts_record_file")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE ts_record_file (
                          id bigint NOT NULL AUTO_INCREMENT,
                          record_id bigint NOT NULL,
                          original_name varchar(255) NOT NULL,
                          url varchar(255) NOT NULL,
                          size bigint NOT NULL,
                          content_type varchar(100) NULL,
                          PRIMARY KEY (id),
                          KEY IDX_ts_record_file_record (record_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建技术服务附件表 ts_record_file");
        }
        ensureColumn(connection, "ts_record_file", "field_key", "varchar(80) NULL");
        ensureKindAsVarchar(connection, "ts_record", "kind", 40);
    }

    private void ensurePurchaseLineTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "pur_purchase")) {
            return;
        }
        if (!tableExists(connection, "pur_purchase_line")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE pur_purchase_line (
                          id bigint NOT NULL AUTO_INCREMENT,
                          purchase_id bigint NOT NULL,
                          item_name varchar(120) NOT NULL,
                          qty decimal(18,3) NOT NULL,
                          unit varchar(20) NOT NULL,
                          amount decimal(18,2) NOT NULL,
                          sort_order int NOT NULL,
                          PRIMARY KEY (id),
                          KEY IDX_pur_purchase_line_purchase (purchase_id),
                          CONSTRAINT FK_pur_purchase_line_purchase FOREIGN KEY (purchase_id) REFERENCES pur_purchase (id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
            log.info("已创建采购明细表 pur_purchase_line");
        }
        try (Statement statement = connection.createStatement()) {
            int inserted = statement.executeUpdate(
                    """
                    INSERT INTO pur_purchase_line (purchase_id, item_name, qty, unit, amount, sort_order)
                    SELECT p.id, p.item_name, p.qty, p.unit, p.amount, 0
                    FROM pur_purchase p
                    WHERE NOT EXISTS (SELECT 1 FROM pur_purchase_line l WHERE l.purchase_id = p.id)
                    """);
            if (inserted > 0) {
                log.info("已将 {} 条采购申请补齐明细行", inserted);
            }
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void ensureCreatedBy(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            return;
        }
        addColumnIfMissing(connection, table, "created_by", "bigint NULL");
        addColumnIfMissing(connection, table, "is_public", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, table, "submit_source", "varchar(16) NOT NULL DEFAULT 'ADMIN'");
    }

    private void ensurePublicColumns(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            return;
        }
        addColumnIfMissing(connection, table, "is_public", "tinyint(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, table, "submit_source", "varchar(16) NOT NULL DEFAULT 'ADMIN'");
    }

    private void ensureColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        addColumnIfMissing(connection, table, column, definition);
    }

    private void ensureColumnNullable(Connection connection, String table, String column) throws SQLException {
        if (!columnExists(connection, table, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NULL");
            } catch (SQLException ignored) {
                statement.execute("ALTER TABLE " + table + " MODIFY " + column + " bigint NULL");
            }
            log.info("已将 {}.{} 改为可空", table, column);
        } catch (SQLException ex) {
            log.warn("未能将 {}.{} 改为可空: {}", table, column, ex.getMessage());
        }
    }

    private boolean addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (columnExists(connection, table, column)) {
            return false;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("已补齐表字段 {}.{}", table, column);
        }
        return true;
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = meta.getTables(catalog, null, table, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getTables(catalog, null, table.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = meta.getColumns(catalog, null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getColumns(catalog, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }
}
