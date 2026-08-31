package com.ltcpond.datapilot.ai;

import java.util.List;

/** 单次 Agent 推理输入，只包含用户问题和脱敏工具观察。 */
public record AgentTurnRequest(
        String question,
        int turn,
        String intent,
        List<AgentObservation> observations) {

    public AgentTurnRequest {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
