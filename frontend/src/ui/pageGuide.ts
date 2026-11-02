import { techKindBySlug } from '../tech/catalog'

export function pageTitle(pathname: string) {
  return pageGuide(pathname).title
}

export function pageGuide(pathname: string): { title: string; hint: string } {
  if (pathname.startsWith('/m')) {
    return { title: '手机办公', hint: '工作台发起申请，待办里办理，个人信息里改资料。' }
  }
  if (pathname === '/') {
    return {
      title: '首页',
      hint: '看待办和我发起的，常用表单从♡添加。',
    }
  }
  if (pathname.startsWith('/inbox')) {
    return {
      title: '待办箱',
      hint: '点一行打开办理。要新发起申请，从应用包进入对应表单。',
    }
  }
  if (pathname.startsWith('/apps')) {
    return {
      title: '应用包',
      hint: '应用包里是分组，分组里是表单。',
    }
  }
  if (pathname.endsWith('/new')) {
    return {
      title: formTitle(pathname, true),
      hint: '先填必填项。点「保存草稿」只存不送审；点「提交办理」才会交给下一办理人。',
    }
  }
  if (isDetailPath(pathname)) {
    return {
      title: formTitle(pathname, false),
      hint: '有编辑权限时点「保存」即可写入。提交办理只在轮到你时出现。',
    }
  }
  if (pathname.startsWith('/workflows')) {
    return {
      title: '流程引擎',
      hint: '点一条流程进入画布。再点节点：设置谁来办、字段能不能改、按钮显不显示。改完要「保存」并「发布」，新单据才会按新流程走。',
    }
  }
  if (pathname.startsWith('/users') || pathname.startsWith('/roles') || pathname.startsWith('/org') || pathname.startsWith('/contacts')) {
    return { title: '通讯录', hint: '左侧切企业架构或角色，右侧看成员。添加成员、添加部门、双击角色可改权限。' }
  }
  if (pathname.startsWith('/theme')) return { title: '主题风格', hint: '只影响显示颜色，不影响办理流程。' }
  if (pathname.startsWith('/profile')) return { title: '个人中心', hint: '修改头像、显示名和登录密码。' }
  return {
    title: listTitle(pathname),
    hint: '点右上角「新建」发起一笔。点表格一行打开已有记录。勾选后可导出。',
  }
}

function isDetailPath(path: string) {
  return /\/\d+(\/|$)/.test(path)
}

function formTitle(pathname: string, creating: boolean) {
  const list = listTitle(pathname.replace(/\/new$/, '').replace(/\/\d+.*$/, ''))
  if (creating && list.startsWith('新建')) return list
  return creating ? `新建${list}` : list
}

function listTitle(pathname: string) {
  if (pathname.startsWith('/customer-opens')) return '新建客户'
  if (pathname.startsWith('/customer-accounts')) return '新建客户账号'
  if (pathname.startsWith('/customers')) return '客户信息'
  if (pathname.startsWith('/devices/repairs')) return '设备维修工单'
  if (pathname.startsWith('/devices/maint-records')) return '维保服务记录'
  if (pathname.startsWith('/devices/moves')) return '设备搬迁工单'
  if (pathname.startsWith('/devices/scraps')) return '设备报废工单'
  if (pathname.startsWith('/portal/banners')) return '轮播图'
  if (pathname.startsWith('/portal/knowledge')) return '知识库'
  if (pathname.startsWith('/portal/company')) return '公司介绍'
  if (pathname.startsWith('/portal/service')) return '服务内容'
  if (pathname.startsWith('/devices')) return '设备信息'
  if (pathname.startsWith('/tech/')) {
    const kind = techKindBySlug(pathname.split('/')[2])
    return kind?.label || '技术服务'
  }
  return '业务办理'
}


