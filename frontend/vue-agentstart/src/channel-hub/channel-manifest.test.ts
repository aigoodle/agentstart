import { describe, expect, it } from 'vitest';
import { accountModelOf, connectionOwnerType } from './channel-manifest';

describe('channel account manifest', () => {
  it('keeps legacy channels personal', () => {
    const model = accountModelOf();
    expect(model).toMatchObject({ scope: 'PERSONAL', ownerRequired: true });
    expect(connectionOwnerType(model)).toBe('USER');
  });

  it('supports tenant channels with a runtime identity bridge', () => {
    const model = accountModelOf({
      provider: 'native', channelId: 'wecom', name: '企业微信', installed: true,
      enabled: true, runtimeStatus: 'ONLINE', metadata: { accountModel: {
        scope: 'TENANT', instancePolicy: 'MULTIPLE',
        identityBridge: { enabled: true, mode: 'OAUTH' },
      } },
    });
    expect(model.ownerRequired).toBe(false);
    expect(model.identityBridge?.mode).toBe('OAUTH');
    expect(connectionOwnerType(model)).toBe('TENANT');
  });
});
