package com.projectmgmt.auth.dto;

import com.projectmgmt.user.dto.UserView;

public record MiniLoginView(UserView user, String token) {
}
