/**
 * Vite dev-server 插件：在 HTTP 层拦截 /api/agent-start/*，
 * 用内存数据库（mock/db.ts）+ 路由表（mock/handlers.ts）直接应答。
 *
 * 之所以必须在 HTTP 层 mock：AgentAppsPage / AgentChatPage / useProviderHub
 * 都不走注入的 client 实例，而是各自 createAgentStartClient，
 * 组件级注入无法覆盖它们；HTTP 层则对所有组件一视同仁。
 */
import type { IncomingMessage, ServerResponse } from 'node:http'

import type { Plugin } from 'vite'

import { createDb } from './db.js'
import type { Ctx } from './handlers.js'
import { matchRoute } from './handlers.js'

const PREFIX = '/api/agent-start'

async function readBody(req: IncomingMessage): Promise<any> {
  const chunks: Buffer[] = []
  for await (const chunk of req) chunks.push(chunk as Buffer)
  if (chunks.length === 0) return undefined
  const raw = Buffer.concat(chunks).toString('utf-8')
  const type = req.headers['content-type'] ?? ''
  if (type.includes('application/json')) {
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  }
  if (type.includes('multipart/form-data')) {
    // 文件上传不做真实解析，交给 upload handler 生成占位文档
    return { __multipart: true }
  }
  return raw
}

function sendJson(res: ServerResponse, status: number, payload: unknown) {
  const text = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(text),
    'Cache-Control': 'no-store',
  })
  res.end(text)
}

export function agentStartMock(): Plugin {
  // 每个 dev server 生命周期一份内存库；重启 dev server 即重置数据。
  const db = createDb()

  return {
    name: 'agent-start-mock',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url ?? ''
        if (!url.startsWith(PREFIX)) {
          next()
          return
        }
        void handle(req, res).catch(next)
      })

      async function handle(req: IncomingMessage, res: ServerResponse) {
        const u = new URL(req.url ?? '', 'http://agent-start-demo')
        let pathname = u.pathname.slice(PREFIX.length)
        if (!pathname.startsWith('/')) pathname = `/${pathname}`
        const method = (req.method ?? 'GET').toUpperCase()

        const matched = matchRoute(method, pathname)
        if (!matched) {
          console.warn(`[mock] 404 ${method} ${pathname}`)
          sendJson(res, 404, {
            code: 'not_found',
            message: `mock 未实现该端点: ${method} ${pathname}`,
          })
          return
        }

        const body = await readBody(req)
        const ctx: Ctx = {
          req,
          res,
          params: matched.params,
          query: u.searchParams,
          body,
          db,
        }

        try {
          const data = await matched.route.handler(ctx)
          // SSE 处理器自行写响应流，此处跳过信封
          if (res.headersSent || res.writableEnded) return
          sendJson(res, 200, { code: 'ok', data: data ?? null })
        } catch (err: any) {
          if (res.headersSent || res.writableEnded) return
          const status = err?.status ?? 500
          console.error(`[mock] ${method} ${pathname} → ${status}`, err?.message ?? err)
          sendJson(res, status, {
            code: 'error',
            message: err?.message ?? 'mock internal error',
            traceId: `mock-${Date.now().toString(36)}`,
          })
        }
      }
    },
  }
}
