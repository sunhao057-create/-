# 表单日期 / 时间字段 SOP

日期、时间、月份字段必须 **点输入框内任意位置都能弹出选择器**，不能只靠右侧小日历图标。

## 实现

- 只用 `frontend/src/ui/DateInput.tsx`，不要写 `<Input type="date" />` / `datetime-local` / `time` / `month`。
- 技术服务 `datetime` 字段同样走 `DateInput`（`type="datetime-local"`）。
- 纯文本占位 `YYYY-MM-DD` 不算日期控件，应改成 `DateInput`。

## 不要做

```tsx
<Input type="date" />
<Input placeholder="YYYY-MM-DD" />
```

```tsx
<DateInput />
<DateInput type="datetime-local" />
<DateInput type="month" />
```

## 表单字段当变量用

联动、过滤、带出展示时，字段名必须 `Form.useWatch('字段名', form)`（或 `Form.Item shouldUpdate` / `getFieldsValue` 回调），禁止把 `customerId` 等当成未声明的 JS 变量。否则会 `is not defined`，在列表+抽屉同页时把整页打挂。
