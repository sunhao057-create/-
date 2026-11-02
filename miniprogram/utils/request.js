const apiBase = require('../config').apiBase

function rejectUnauthorized(url, res, reject) {
  const message = (res.data && res.data.message) || ''
  const loggingIn = url.indexOf('/api/auth/login') >= 0 || url.indexOf('/api/auth/mini-login') >= 0
  if (!loggingIn) {
    wx.removeStorageSync('token')
    wx.removeStorageSync('user')
    wx.reLaunch({ url: '/pages/login/index' })
    reject(new Error('请先登录'))
    return
  }
  reject(new Error(message || '用户名或密码错误'))
}

function unwrap(data) {
  if (data && typeof data.code === 'number' && data.code !== 0) {
    const err = new Error(data.message || '请求失败')
    err.code = data.code
    throw err
  }
  return data ? data.data : data
}

function request(url, method, data, extraHeader) {
  const token = wx.getStorageSync('token')
  const headers = {
    'Content-Type': 'application/json'
  }
  const more = extraHeader || {}
  for (const key in more) {
    if (Object.prototype.hasOwnProperty.call(more, key)) {
      headers[key] = more[key]
    }
  }
  if (token) {
    headers.Authorization = 'Bearer ' + token
  }
  return new Promise(function (resolve, reject) {
    wx.request({
      url: apiBase + url,
      method: method || 'GET',
      data: data,
      header: headers,
      enableCookie: true,
      success: function (res) {
        if (res.statusCode === 401) {
          rejectUnauthorized(url, res, reject)
          return
        }
        if (res.statusCode >= 400) {
          const message = (res.data && res.data.message) || '请求失败'
          reject(new Error(message))
          return
        }
        try {
          resolve(unwrap(res.data))
        } catch (e) {
          reject(e)
        }
      },
      fail: function (err) {
        reject(new Error(err.errMsg || '网络错误'))
      }
    })
  })
}

function get(url, data) {
  return request(url, 'GET', data)
}

function post(url, data) {
  return request(url, 'POST', data)
}

function put(url, data) {
  return request(url, 'PUT', data)
}

function formRequest(url, method, data) {
  const token = wx.getStorageSync('token')
  const headers = {
    'Content-Type': 'application/x-www-form-urlencoded'
  }
  if (token) {
    headers.Authorization = 'Bearer ' + token
  }
  return new Promise(function (resolve, reject) {
    wx.request({
      url: apiBase + url,
      method: method || 'POST',
      data: data,
      header: headers,
      enableCookie: true,
      success: function (res) {
        if (res.statusCode === 401) {
          rejectUnauthorized(url, res, reject)
          return
        }
        if (res.statusCode >= 400) {
          const message = (res.data && res.data.message) || '请求失败'
          reject(new Error(message))
          return
        }
        try {
          resolve(unwrap(res.data))
        } catch (e) {
          reject(e)
        }
      },
      fail: function (err) {
        reject(new Error(err.errMsg || '网络错误'))
      }
    })
  })
}

function postForm(url, data) {
  return formRequest(url, 'POST', data)
}

function putForm(url, data) {
  return formRequest(url, 'PUT', data)
}

function del(url, data) {
  return request(url, 'DELETE', data)
}

function upload(url, filePath, formData) {
  const token = wx.getStorageSync('token')
  const headers = {}
  if (token) {
    headers.Authorization = 'Bearer ' + token
  }
  return new Promise(function (resolve, reject) {
    wx.uploadFile({
      url: apiBase + url,
      filePath: filePath,
      name: 'files',
      formData: formData || {},
      header: headers,
      success: function (res) {
        if (res.statusCode === 401) {
          rejectUnauthorized(url, res, reject)
          return
        }
        if (res.statusCode >= 400) {
          let message = '上传失败'
          try {
            const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
            if (body && body.message) message = body.message
          } catch (e) {}
          reject(new Error(message))
          return
        }
        try {
          const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          resolve(unwrap(body))
        } catch (e) {
          resolve(res.data)
        }
      },
      fail: function (err) {
        reject(new Error(err.errMsg || '上传失败'))
      }
    })
  })
}

module.exports = {
  apiBase: apiBase,
  request: request,
  get: get,
  post: post,
  put: put,
  del: del,
  upload: upload,
  postForm: postForm,
  putForm: putForm
}
