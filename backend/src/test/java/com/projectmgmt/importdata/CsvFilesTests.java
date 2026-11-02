package com.projectmgmt.importdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.FormModule;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvFilesTests {

    @Test
    void skipsHintRowThenReadsData() {
        byte[] template = CsvFiles.template(
                List.of("客户简称", "客户编码"),
                List.of("提示：必填", "提示：唯一")
        );
        String csv = new String(template, StandardCharsets.UTF_8) + "冠礼,IMP1\n";

        var table = CsvFiles.parse(csv.getBytes(StandardCharsets.UTF_8), List.of("客户简称", "客户编码"));

        assertThat(table.rows()).hasSize(1);
        assertThat(CsvFiles.require(table.rows().get(0), "客户简称")).isEqualTo("冠礼");
        assertThat(CsvFiles.cell(table.rows().get(0), "客户编码")).isEqualTo("IMP1");
    }

    @Test
    void stillAcceptsOldCommentThenHeaderThenHintLayout() {
        String csv = """
                # 旧版说明
                客户简称,客户编码
                提示：必填,提示：唯一
                冠礼,IMP2
                """;

        var table = CsvFiles.parse(csv.getBytes(StandardCharsets.UTF_8), List.of("客户简称", "客户编码"));

        assertThat(table.rows()).hasSize(1);
        assertThat(CsvFiles.require(table.rows().get(0), "客户编码")).isEqualTo("IMP2");
    }

    @Test
    void downloadedTemplatePutsHintsOnFirstRow() {
        byte[] bytes = CsvTemplates.of(FormModule.CUSTOMER).bytes();
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        String[] lines = text.split("\\r?\\n", -1);

        assertThat(lines[0]).startsWith("提示：必填");
        assertThat(lines[1]).startsWith("客户简称");
        assertThat(text).doesNotContain("第3行为填写提示");
        assertThatThrownBy(() -> CsvFiles.parse(bytes, CsvTemplates.of(FormModule.CUSTOMER).headers()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("没有可导入的数据");
    }

    @Test
    void missingHeaderIsRejected() {
        byte[] csv = "客户简称\n冠礼\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> CsvFiles.parse(csv, List.of("客户简称", "客户编码")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("客户编码");
    }
}
