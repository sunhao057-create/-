import type { ThemeConfig } from 'antd'

export const appTheme: ThemeConfig = {
  token: {
    colorPrimary: '#171717',
    colorInfo: '#171717',
    colorSuccess: '#52c41a',
    colorBgLayout: '#f6f6f6',
    colorBgContainer: '#ffffff',
    colorBorder: '#ececec',
    colorBorderSecondary: '#f0f0f0',
    colorText: '#171717',
    colorTextSecondary: '#8c8c8c',
    borderRadius: 10,
    fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
    controlHeight: 36,
    boxShadow: 'none',
    boxShadowSecondary: 'none',
  },
  components: {
    Layout: {
      siderBg: '#ffffff',
      headerBg: '#ffffff',
      headerHeight: 64,
      headerPadding: '0 28px',
      bodyBg: '#f6f6f6',
    },
    Menu: {
      itemBg: 'transparent',
      itemSelectedBg: '#f2f2f2',
      itemHoverBg: '#f7f7f7',
      itemSelectedColor: '#171717',
      itemColor: '#595959',
      itemBorderRadius: 8,
      itemMarginInline: 10,
      itemMarginBlock: 2,
      itemHeight: 40,
      iconSize: 16,
      activeBarBorderWidth: 0,
      activeBarWidth: 0,
    },
    Button: {
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow: 'none',
    },
    Card: {
      headerFontSize: 16,
      headerFontSizeSM: 14,
      paddingLG: 20,
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: '#8c8c8c',
      rowHoverBg: '#fafafa',
      headerSplitColor: 'transparent',
    },
    Tabs: {
      itemSelectedColor: '#171717',
      inkBarColor: '#171717',
      itemColor: '#8c8c8c',
      itemHoverColor: '#171717',
    },
    Input: {
      activeShadow: 'none',
    },
    Modal: {
      boxShadow: '0 12px 40px rgba(17, 17, 17, 0.08)',
    },
    Tag: {
      defaultBg: '#f5f5f5',
      defaultColor: '#595959',
    },
  },
}
