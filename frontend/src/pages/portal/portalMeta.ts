export type PortalKind = 'MINI_BANNER' | 'MINI_KNOWLEDGE' | 'MINI_COMPANY' | 'MINI_SERVICE'

export const PORTAL_META: Record<
  PortalKind,
  { title: string; path: string; categories: string[]; coverLabel: string }
> = {
  MINI_BANNER: { title: '轮播图', path: '/portal/banners', categories: [], coverLabel: '轮播图片' },
  MINI_KNOWLEDGE: { title: '知识库', path: '/portal/knowledge', categories: ['知识中心'], coverLabel: '封面' },
  MINI_COMPANY: {
    title: '公司介绍',
    path: '/portal/company',
    categories: ['工作缩影', '服务介绍'],
    coverLabel: '封面',
  },
  MINI_SERVICE: {
    title: '服务内容',
    path: '/portal/service',
    categories: ['顶部横幅', '标准更新', '技术分享', '生活百科', '新闻中心', '搬迁服务承诺', '报修服务承诺'],
    coverLabel: '封面',
  },
}
