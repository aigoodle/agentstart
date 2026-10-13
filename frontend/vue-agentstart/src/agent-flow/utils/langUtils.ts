import { createVNode } from 'vue';

import { ExclamationCircleOutlined } from '@ant-design/icons-vue';
import { message, Modal } from '../../ui';

const config = { hostname: '' };

/**
 * Base used to resolve stored-file references into `<img>` src URLs.
 * Centralised here (instead of scattering `/api/agent-start/storage/file/…`
 * across components) so hosts with a different proxy/namespace override it
 * once via {@link setStorageFileBase}. SSR-safe: pure string, no DOM.
 */
let storageFileBase = '/api/agent-start/storage/file';

export function setStorageFileBase(base: string) {
  storageFileBase = base.replace(/\/+$/, '');
}

export function getStorageFileBase(): string {
  return storageFileBase;
}

export default {
  getId(str: never | string = ''): string {
    if (!str) {
      str = '';
    }
    // 取时间戳后6位 + 3位随机数
    return `${str}_${Date.now().toString().slice(-6)}${Math.random().toString(36).slice(-3)}`;
  },
  getResponseImageUri(datas: any): string {
    if (datas) {
      const data = datas[0];
      return `${data.bucketName}/${data.objectName}`;
    }
    return '';
  },
  getImageUrl(str: never | string = ''): string {
    if (str) {
      if (str.includes('data:image/')) {
        return str;
      }
      if (str.startsWith('http://') || str.startsWith('https://')) {
        return str;
      }
      return `${getStorageFileBase()}/${str}`;
    }
    return '';
  },
  getQueryString(name: string) {
    // SSR guard: no location on the server.
    if (typeof window === 'undefined') return null;
    const reg = new RegExp(`(^|&)${name}=([^&]*)(&|$)`, 'i');
    const r = window.location.search.slice(1).match(reg);
    if (r) {
      return unescape(r[2]);
    }
    return null;
  },
  message(ar: any) {
    if (ar.error) {
      this.error(ar.message);
      return false;
    } else if (ar.success) {
      this.info(ar.message);
      return true;
    } else {
      this.warn(ar.message);
      return false;
    }
  },
  info(msg: any) {
    if (!msg) {
      msg = '完成';
    }
    message.info(msg);
  },
  warn(msg: any) {
    message.warning(msg);
  },
  error(msg: any) {
    message.error(msg);
  },
  confirm(prop: any, ok?: any, cancel?: any) {
    prop = prop || {};
    Modal.confirm({
      maskClosable: false,
      title: prop.title || '确定要继续操作吗?',
      icon: createVNode(ExclamationCircleOutlined),
      content: createVNode(
        'div',
        {
          style: 'color:red;',
        },
        prop.message || '',
      ),
      onOk() {
        if (ok) {
          ok();
        }
      },
      onCancel() {
        if (cancel) {
          cancel();
        }
      },
      class: 'test',
    });
  },
  copy(from: any, to: any) {
    for (const attr in from) {
      // eslint-disable-next-line eqeqeq
      if (from[attr] !== null || from[attr] != undefined) {
        to[attr] = from[attr];
      }
    }
  },
  clone(obj: any) {
    let o: any;
    switch (typeof obj) {
      case 'boolean': {
        o = obj;
        break;
      }
      case 'number': {
        o = obj - 0;
        break;
      }
      case 'object': {
        if (obj === null) {
          o = null;
        } else {
          if (Array.isArray(obj)) {
            o = [];
            for (let i = 0, len = obj.length; i < len; i++) {
              o.push(this.clone(obj[i]));
            }
          } else {
            o = {};
            for (const k in obj) {
              o[k] = this.clone(obj[k]);
            }
          }
        }
        break;
      }
      case 'string': {
        o = `${obj}`;
        break;
      }
      case 'undefined': {
        break;
      }
      default: {
        o = obj;
        break;
      }
    }
    return o;
  },
  buildTreeData(data: any, rootId: string) {
    if (!data) {
      return [];
    }
    if (!rootId) {
      rootId = '0';
    }
    const jsonstr = JSON.stringify(data);
    const cloneData = JSON.parse(jsonstr);
    const newData = cloneData.filter((father: any) => {
      const branchArr = cloneData.filter(
        (child: any) => father.id === child.parentId,
      );
      if (branchArr.length > 0) {
        father.children = branchArr;
      } else {
        father.children = [];
        father.isLeaf = true; // 设置为叶子节点
      }
      return father.parentId === rootId;
    });
    return newData.length > 0 ? newData : cloneData;
  },
  cdn(url: string) {
    if (!url) return '';
    if (url.indexOf('http') === 0) {
      return url;
    }
    return config.hostname + url;
  },
};
