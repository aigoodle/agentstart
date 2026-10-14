package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

/**
 * Runs a JEXL expression / script with access to the current variable pool. Config:
 * <ul>
 *   <li>{@code code} — a JEXL script (last expression = return value)</li>
 *   <li>{@code outputKey} — where to store the return value (default {@code result})</li>
 * </ul>
 * The script sees:
 * <ul>
 *   <li>{@code sys.*} — run-level inputs</li>
 *   <li>{@code &lt;nodeId&gt;.*} — every executed node's outputs</li>
 * </ul>
 * Only registered when JEXL is on the classpath. Kept intentionally sandbox-lite:
 * JEXL 3 already forbids `Runtime.exec` etc. by default; applications embedding this
 * should keep the code behind trusted authoring.
 */
public class CodeNodeExecutor implements NodeExecutor {

    private final JexlEngine scriptEngine =
            new JexlBuilder().safe(true).silent(true).strict(false).create();

    @Override
    public NodeType type() {
        return NodeType.CODE;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        String sourceCode = node.getString("code");
        if (sourceCode == null || sourceCode.isBlank()) {
            return NodeResult.failure("Code node requires 'code'");
        }
        try {
            JexlScript script = scriptEngine.createScript(sourceCode);
            Object returnValue = script.execute(buildScriptContext(context));
            return NodeResult.of(node.getString("outputKey", "result"), returnValue);
        } catch (RuntimeException exception) {
            return NodeResult.failure("Code node failed: " + exception.getMessage());
        }
    }

    private static JexlContext buildScriptContext(ExecutionContext context) {
        MapContext scriptContext = new MapContext();
        Map<String, Map<String, Object>> variablesByNode = context.getPool().snapshot();
        for (Map.Entry<String, Map<String, Object>> nodeVariables : variablesByNode.entrySet()) {
            scriptContext.set(nodeVariables.getKey(), new HashMap<>(nodeVariables.getValue()));
        }
        scriptContext.set("inputs", new HashMap<>(context.getInputs()));
        return scriptContext;
    }
}
