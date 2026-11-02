# 项目管理系统 — 账号管理设计

日期：2026-08-24  
范围：第一期只做账号管理，项目管理内容占位。

## 目标

内部员工与外部客户共用一套系统，用角色区分。管理员开户，用户名 + 密码登录。网页自适应电脑和手机。

## 技术栈

- 后端：Java 17、Spring Boot、Spring Security（Session Cookie）
- 前端：React（Vite），本地开发走 Vite 代理
- 数据库：MySQL 8

## 角色与权限

- `ADMIN`：账号增删改、启用/停用、重置密码、查看全部登录日志
- `USER`：个人资料、头像、改密码、自己的登录日志
- 自定义角色以后再加
- 不能自助注册；不能停用自己；不能改用户名
- 新开账号或被重置密码后，首次登录必须改密码

## 页面

- 登录
- 强制改密
- 项目管理（占位）
- 账号管理（管理员）
- 个人中心（资料 / 头像 / 改密码 / 登录日志）

## 数据表

### sys_user

id, username(unique), password_hash, display_name, avatar_url, role, status, must_change_password, created_at, updated_at

### sys_login_log

id, user_id(nullable), username, success, fail_reason, ip, user_agent, created_at

## 接口

均挂在 `/api`，登录态为 Session。

- POST `/api/auth/login`
- POST `/api/auth/logout`
- GET `/api/auth/me`
- PUT `/api/auth/password`
- PUT `/api/profile`
- POST `/api/profile/avatar`
- GET `/api/profile/login-logs`
- GET/POST `/api/users`
- PUT `/api/users/{id}`
- PUT `/api/users/{id}/status`
- PUT `/api/users/{id}/password`
- GET `/api/users/login-logs`

## 错误处理

- 未登录 401，无权限 403
- 业务错误返回 `{ code, message, data }`，文案中文
- 登录失败统一「用户名或密码错误」，停用账号提示「账号已停用」
- 用户名重复、不能停用自己等返回 400

## 测试约定

- 后端至少覆盖：登录成功/失败、管理员接口拒绝普通用户
- 前端在浏览器走通：登录、改密、账号列表、新建用户

## 初始化账号

用户名 `admin`，密码 `Admin123!`，角色管理员。
