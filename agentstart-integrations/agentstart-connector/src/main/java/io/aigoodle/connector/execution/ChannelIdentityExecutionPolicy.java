package io.aigoodle.connector.execution;

import io.aigoodle.connector.ConnectorActionDefinition;
import io.aigoodle.connector.ConnectorRiskLevel;

/** Prevents an unverified external channel principal from mutating enterprise systems. */
public class ChannelIdentityExecutionPolicy implements ConnectorExecutionPolicy {
    @Override
    public Decision evaluate(ConnectorActionDefinition action, ConnectorExecutionRequest request) {
        var attributes = request.context().attributes();
        if (!attributes.containsKey("channelId")) return Decision.allow();
        boolean verified = Boolean.TRUE.equals(attributes.get("externalIdentityVerified"));
        if (!verified && action.riskLevel() != ConnectorRiskLevel.READ) {
            return Decision.deny("外部消息身份尚未完成企业身份验证，禁止执行写入或破坏性操作");
        }
        return Decision.allow();
    }
}
