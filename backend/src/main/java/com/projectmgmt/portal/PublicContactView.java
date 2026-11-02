package com.projectmgmt.portal;

import java.util.List;

public record PublicContactView(String name, String title, String phone, String email, boolean primaryContact) {}
