import type { ChannelAccountModel, ChannelDefinition } from './types';

const PERSONAL_DEFAULT: ChannelAccountModel = {
  scope: 'PERSONAL', instancePolicy: 'MULTIPLE', ownerRequired: true,
  ownerLabel: '员工 / 所有者 ID', ownerPlaceholder: '例如 employee-001',
};

/** Resolve the backend manifest while preserving legacy channel definitions. */
export function accountModelOf(channel?: ChannelDefinition): ChannelAccountModel {
  const raw = channel?.metadata?.accountModel;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { ...PERSONAL_DEFAULT };
  const model = raw as Partial<ChannelAccountModel>;
  const scope = model.scope === 'TENANT' ? 'TENANT' : 'PERSONAL';
  return {
    scope,
    instancePolicy: model.instancePolicy === 'SINGLE' ? 'SINGLE' : 'MULTIPLE',
    ownerRequired: model.ownerRequired ?? scope === 'PERSONAL',
    ownerLabel: model.ownerLabel ?? PERSONAL_DEFAULT.ownerLabel,
    ownerPlaceholder: model.ownerPlaceholder ?? PERSONAL_DEFAULT.ownerPlaceholder,
    identityBridge: model.identityBridge,
  };
}

export const connectionOwnerType = (model: ChannelAccountModel) =>
  model.scope === 'TENANT' ? 'TENANT' : 'USER';
