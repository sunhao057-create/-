package com.projectmgmt.importdata;

import java.util.List;

public record ImportResult(int created, int failed, List<String> errors) {
}
