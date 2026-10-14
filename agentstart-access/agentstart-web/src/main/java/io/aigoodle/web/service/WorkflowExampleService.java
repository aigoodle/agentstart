package io.aigoodle.web.service;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.graph.WorkflowGraph;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Curated set of example workflows a first-time user can one-click load in the
 * playground. Each entry is intentionally small and single-purpose so it can be
 * read and modified without study — the exact opposite of "boilerplate demo".
 * <p>
 * The list is code-defined (not stored in the database) because it's always the
 * same for a given release: think of it as canned onboarding content, alongside the
 * seeded prompt templates.
 */
@Service
public class WorkflowExampleService {

    public List<WorkflowExample> examples() {
        return List.of(template(), ifElseBranch(), httpFetch(), listOperator(), iteration());
    }

    private static WorkflowExample template() {
        WorkflowGraph g = JsonUtils.parse(
                """
                {
                  "nodes": [
                    { "id": "start", "type": "START" },
                    {
                      "id": "greet",
                      "type": "TEMPLATE_TRANSFORM",
                      "data": {
                        "template": "你好，{{#sys.name#}}。今天是 {{#sys.today#}}。",
                        "outputKey": "line"
                      }
                    },
                    {
                      "id": "end",
                      "type": "END",
                      "data": { "outputs": { "greeting": "{{#greet.line#}}" } }
                    }
                  ],
                  "edges": [
                    { "source": "start", "target": "greet" },
                    { "source": "greet", "target": "end" }
                  ]
                }
                """,
                WorkflowGraph.class);
        return new WorkflowExample(
                "template-hello",
                "模板问候",
                "最小示例：一个模板节点渲染变量并输出。适合理解 `{{#sys.x#}}` 语法。",
                Map.of("name", "Alice", "today", "2026-07-09"),
                g);
    }

    private static WorkflowExample ifElseBranch() {
        WorkflowGraph g = JsonUtils.parse(
                """
                {
                  "nodes": [
                    { "id": "start", "type": "START" },
                    {
                      "id": "check",
                      "type": "IF_ELSE",
                      "data": {
                        "logicalOperator": "and",
                        "conditions": [
                          { "variable": "sys.vip", "operator": "equals", "value": "true" }
                        ]
                      }
                    },
                    {
                      "id": "vip",
                      "type": "TEMPLATE_TRANSFORM",
                      "data": { "template": "🌟 VIP {{#sys.name#}}", "outputKey": "msg" }
                    },
                    {
                      "id": "normal",
                      "type": "TEMPLATE_TRANSFORM",
                      "data": { "template": "普通用户 {{#sys.name#}}", "outputKey": "msg" }
                    },
                    {
                      "id": "endVip",
                      "type": "END",
                      "data": { "outputs": { "answer": "{{#vip.msg#}}" } }
                    },
                    {
                      "id": "endNormal",
                      "type": "END",
                      "data": { "outputs": { "answer": "{{#normal.msg#}}" } }
                    }
                  ],
                  "edges": [
                    { "source": "start", "target": "check" },
                    { "source": "check", "target": "vip", "sourceHandle": "true" },
                    { "source": "check", "target": "normal", "sourceHandle": "false" },
                    { "source": "vip", "target": "endVip" },
                    { "source": "normal", "target": "endNormal" }
                  ]
                }
                """,
                WorkflowGraph.class);
        return new WorkflowExample(
                "if-else-branch",
                "条件分支",
                "展示 IF_ELSE 节点：VIP 与普通用户走不同分支到不同 END。",
                Map.of("name", "Bob", "vip", "true"),
                g);
    }

    private static WorkflowExample httpFetch() {
        WorkflowGraph g = JsonUtils.parse(
                """
                {
                  "nodes": [
                    { "id": "start", "type": "START" },
                    {
                      "id": "call",
                      "type": "HTTP_REQUEST",
                      "data": {
                        "method": "GET",
                        "url": "https://httpbin.org/uuid",
                        "timeoutSeconds": 10
                      }
                    },
                    {
                      "id": "end",
                      "type": "END",
                      "data": { "outputs": { "status": "{{#call.status#}}", "body": "{{#call.body#}}" } }
                    }
                  ],
                  "edges": [
                    { "source": "start", "target": "call" },
                    { "source": "call", "target": "end" }
                  ]
                }
                """,
                WorkflowGraph.class);
        return new WorkflowExample(
                "http-fetch", "HTTP 请求", "调用外部 API（这里用 httpbin.org 返回一个 UUID）。展示 HTTP_REQUEST 节点。", Map.of(), g);
    }

    private static WorkflowExample listOperator() {
        WorkflowGraph g = JsonUtils.parse(
                """
                {
                  "nodes": [
                    { "id": "start", "type": "START" },
                    {
                      "id": "seed",
                      "type": "VARIABLE_ASSIGNER",
                      "data": {
                        "assignments": [
                          {
                            "name": "items",
                            "value": [
                              { "id": "b", "score": 2 },
                              { "id": "c", "score": 3 },
                              { "id": "a", "score": 1 }
                            ]
                          }
                        ]
                      }
                    },
                    {
                      "id": "sort",
                      "type": "LIST_OPERATOR",
                      "data": {
                        "inputList": "seed.items",
                        "operation": "sort",
                        "field": "score",
                        "order": "desc"
                      }
                    },
                    {
                      "id": "end",
                      "type": "END",
                      "data": { "outputs": { "sorted": "{{#sort.result#}}" } }
                    }
                  ],
                  "edges": [
                    { "source": "start", "target": "seed" },
                    { "source": "seed", "target": "sort" },
                    { "source": "sort", "target": "end" }
                  ]
                }
                """,
                WorkflowGraph.class);
        return new WorkflowExample(
                "list-sort",
                "列表排序",
                "变量赋值 → 列表算子（按 score 降序排序）→ 输出。展示 VARIABLE_ASSIGNER + LIST_OPERATOR。",
                Map.of(),
                g);
    }

    private static WorkflowExample iteration() {
        WorkflowGraph g = JsonUtils.parse(
                """
                {
                  "nodes": [
                    { "id": "start", "type": "START" },
                    {
                      "id": "seed",
                      "type": "VARIABLE_ASSIGNER",
                      "data": {
                        "assignments": [ { "name": "names", "value": ["Alice", "Bob", "Carol"] } ]
                      }
                    },
                    {
                      "id": "loop",
                      "type": "ITERATION",
                      "data": {
                        "inputList": "seed.names",
                        "outputKey": "greetings",
                        "subGraph": {
                          "nodes": [
                            { "id": "s", "type": "START" },
                            {
                              "id": "hi",
                              "type": "TEMPLATE_TRANSFORM",
                              "data": { "template": "hello {{#sys.item#}}", "outputKey": "line" }
                            },
                            {
                              "id": "e",
                              "type": "END",
                              "data": { "outputs": { "line": "{{#hi.line#}}" } }
                            }
                          ],
                          "edges": [
                            { "source": "s", "target": "hi" },
                            { "source": "hi", "target": "e" }
                          ]
                        }
                      }
                    },
                    {
                      "id": "end",
                      "type": "END",
                      "data": { "outputs": { "greetings": "{{#loop.greetings#}}" } }
                    }
                  ],
                  "edges": [
                    { "source": "start", "target": "seed" },
                    { "source": "seed", "target": "loop" },
                    { "source": "loop", "target": "end" }
                  ]
                }
                """,
                WorkflowGraph.class);
        return new WorkflowExample("iteration-foreach", "循环遍历列表", "ITERATION 节点：为列表中每一项运行一个子图，聚合结果。", Map.of(), g);
    }

    /** DTO returned by the /workflow-examples endpoint. */
    public record WorkflowExample(
            String id, String name, String description, Map<String, Object> defaultInputs, WorkflowGraph graph) {}
}
