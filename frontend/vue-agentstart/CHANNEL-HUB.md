# Channel Hub

`channel-hub` manages long-running message gateway accounts such as QQBot,
Weixin/WeCom, Feishu and DingTalk. It is intentionally separate from
`connector-hub`, whose connectors are actions invoked by an Agent or workflow.

```ts
import { ChannelHubApp } from 'vue-agent-start/channel-hub';
```

```vue
<ChannelHubApp :client="agentStartClient" />
```

Use `client.channels` for channel catalog, account, connection, conversation
and message operations; the channel model types (`ChannelDefinition`,
`ChannelConnection`, `MyRobot`, …) are declared in `channel-hub/types`.
`client.connectors` is reserved for callable business connectors and no longer
exposes channel methods, and channel components are exported only from
`vue-agent-start/channel-hub` (no `connector-hub` compatibility re-exports).

The backend `provider` identifies the gateway runtime (for example `native` or
`hermes`), while `channelId` identifies the platform adapter (for example
`qqbot` or `weixin`). This lets multiple gateway implementations expose the
same platform without merging them into business connectors.

## Backend-driven account forms

Channel modules should declare credentials, configuration, and account
semantics in their backend descriptor. YAML is a good authoring format, but it
must be validated by the backend and exposed through `GET /channels`; the
browser should not fetch module YAML files directly.

```yaml
channel:
  id: wecom
  credentialSchema:
    type: object
    required: [corpId, agentId, secret]
    properties:
      corpId: { type: string, title: Corp ID }
      agentId: { type: string, title: Agent ID }
      secret: { type: string, title: Secret, writeOnly: true }
  metadata:
    accountModel:
      scope: TENANT
      instancePolicy: MULTIPLE
      ownerRequired: false
      identityBridge:
        enabled: true
        mode: OAUTH
        externalIdentityLabel: 企业微信用户
        enterpriseIdentityLabel: 员工
        description: 首次交互时引导用户完成员工身份绑定。
```

`PERSONAL` remains the compatibility default and is saved with `ownerType:
USER`. `TENANT` does not ask for a personal owner and is saved with `ownerType:
TENANT`; its identity bridge is the separate runtime mapping from an external
sender to an enterprise employee. Credential and configuration fields continue
to use JSON Schema. OAuth callbacks, QR login, and device authorization remain
explicit channel extensions rather than form fields.
